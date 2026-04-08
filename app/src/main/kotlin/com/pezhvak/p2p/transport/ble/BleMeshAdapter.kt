package com.pezhvak.p2p.transport.ble

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import android.os.Build
import android.os.ParcelUuid
import com.pezhvak.p2p.core.crypto.*
import com.pezhvak.p2p.core.identity.KeyManager
import com.pezhvak.p2p.transport.ble.BleProtocol.BlePacket
import com.pezhvak.p2p.transport.ble.BleProtocol.MeshPacket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BLE Mesh Transport Adapter.
 *
 * Roles:
 *  - ADVERTISER: Makes device discoverable to peers.
 *  - SCANNER:    Discovers nearby peers and initiates connections.
 *  - GATT SERVER: Accepts incoming connections and exposes characteristics.
 *  - GATT CLIENT: Connects to discovered servers, sends/receives data.
 *
 * Mesh topology: each node connects to up to MAX_PEERS peers.
 * Routing uses epidemic/flooding with TTL to prevent infinite loops.
 * Message IDs are remembered in a rolling window to prevent re-processing.
 *
 * Encryption: all payloads are NIP-44 encrypted between endpoints;
 * intermediate relay nodes see only ciphertext and routing headers.
 */
@Singleton
class BleMeshAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyManager: KeyManager,
) {
    companion object {
        const val MAX_PEERS = 8
        const val SCAN_INTERVAL_MS = 10_000L
        const val ADVERTISE_TIMEOUT_MS = 0  // 0 = forever
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter

    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null

    // Connected GATT clients (central role, we initiated)
    private val connectedCentrals = ConcurrentHashMap<String, BluetoothGatt>()
    // Connected peripherals (server role, they connected to us)
    private val connectedPeripherals = ConcurrentHashMap<String, BluetoothDevice>()

    // Packet reassembly buffers keyed by device+sessionId
    private val reassemblyBuffers = ConcurrentHashMap<String, MutableList<BlePacket>>()

    // Seen mesh message IDs (rolling window to prevent loops)
    private val seenMessageIds = LruSet<Long>(maxSize = 1_000)

    private val _receivedPackets = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 256)
    val receivedPackets: SharedFlow<MeshPacket> = _receivedPackets.asSharedFlow()

    private val _peerDiscovered = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val peerDiscovered: SharedFlow<String> = _peerDiscovered.asSharedFlow()

    val isBluetoothSupported: Boolean get() = adapter != null
    val isBluetoothEnabled: Boolean get() = adapter?.isEnabled == true

    // ─── Lifecycle ──────────────────────────────────────────────────────────

    fun start() {
        if (!isBluetoothEnabled) return
        startGattServer()
        startAdvertising()
        startScanning()
    }

    fun stop() {
        advertiser?.stopAdvertising(advertiseCallback)
        scanner?.stopScan(scanCallback)
        gattServer?.close()
        connectedCentrals.values.forEach { it.close() }
        connectedCentrals.clear()
        connectedPeripherals.clear()
        scope.cancel()
    }

    // ─── GATT Server ─────────────────────────────────────────────────────────

    private fun startGattServer() {
        val writeChar = BluetoothGattCharacteristic(
            BleProtocol.CHAR_WRITE_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        val notifyChar = BluetoothGattCharacteristic(
            BleProtocol.CHAR_NOTIFY_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        ).also {
            it.addDescriptor(BluetoothGattDescriptor(
                BleProtocol.CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            ))
        }
        val identityChar = BluetoothGattCharacteristic(
            BleProtocol.CHAR_IDENTITY_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        ).also { char ->
            // Expose our pubkey so peers can bootstrap routing
            char.value = keyManager.getPrivateKeyBytes()
                .let { Secp256k1.getXOnlyPublicKey(it) }
        }

        val service = BluetoothGattService(BleProtocol.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(writeChar)
        service.addCharacteristic(notifyChar)
        service.addCharacteristic(identityChar)

        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        gattServer?.addService(service)
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedPeripherals[device.address] = device
                    scope.launch { _peerDiscovered.emit(device.address) }
                }
                BluetoothProfile.STATE_DISCONNECTED -> connectedPeripherals.remove(device.address)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean,
            offset: Int, value: ByteArray
        ) {
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
            if (characteristic.uuid == BleProtocol.CHAR_WRITE_UUID) {
                scope.launch { handleIncomingData(device.address, value) }
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean,
            offset: Int, value: ByteArray
        ) {
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }
    }

    // ─── Scanning ────────────────────────────────────────────────────────────

    private fun startScanning() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        scanner = adapter?.bluetoothLeScanner
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BleProtocol.SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()
        scanner?.startScan(listOf(filter), settings, scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (!connectedCentrals.containsKey(device.address) &&
                connectedCentrals.size < MAX_PEERS) {
                connectToDevice(device)
            }
        }
    }

    // ─── Advertising ─────────────────────────────────────────────────────────

    private fun startAdvertising() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        advertiser = adapter?.bluetoothLeAdvertiser ?: return
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .setTimeout(ADVERTISE_TIMEOUT_MS)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(BleProtocol.SERVICE_UUID))
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()
        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            // Advertising not supported on this hardware – that's OK
        }
    }

    // ─── GATT Client ────────────────────────────────────────────────────────

    private fun connectToDevice(device: BluetoothDevice) {
        device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        connectedCentrals[device.address] = gatt
                        gatt.requestMtu(BleProtocol.PREFERRED_MTU)
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        connectedCentrals.remove(device.address)
                        gatt.close()
                    }
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {}

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(BleProtocol.SERVICE_UUID) ?: return
                    // Enable notifications on NOTIFY characteristic
                    val notifyChar = service.getCharacteristic(BleProtocol.CHAR_NOTIFY_UUID) ?: return
                    gatt.setCharacteristicNotification(notifyChar, true)
                    val descriptor = notifyChar.getDescriptor(BleProtocol.CCCD_UUID)
                    descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                    scope.launch { _peerDiscovered.emit(device.address) }
                }
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                if (characteristic.uuid == BleProtocol.CHAR_NOTIFY_UUID) {
                    scope.launch { handleIncomingData(gatt.device.address, characteristic.value) }
                }
            }
        }, BluetoothDevice.TRANSPORT_LE)
    }

    // ─── Send ────────────────────────────────────────────────────────────────

    /**
     * Send encrypted mesh packet to all peers (flood routing).
     * Each hop decrements TTL; nodes with TTL=0 don't forward.
     */
    fun sendMeshPacket(packet: MeshPacket) {
        val data = MeshPacket.serialize(packet)
        // Split ByteArray into chunks of MAX_PAYLOAD bytes
        val chunkSize = BleProtocol.MAX_PAYLOAD
        val numChunks = (data.size + chunkSize - 1) / chunkSize
        val sessionId = (Math.random() * Short.MAX_VALUE).toInt().toShort()

        for (index in 0 until numChunks) {
            val start = index * chunkSize
            val end = minOf(start + chunkSize, data.size)
            val chunkBytes = data.copyOfRange(start, end)
            val blePacket = BlePacket(
                type = if (packet.dstPubKey.contentEquals(MeshPacket.BROADCAST_ADDR))
                    BleProtocol.TYPE_MESH_RELAY else BleProtocol.TYPE_MESSAGE,
                chunkIndex = index.toShort(),
                totalChunks = numChunks.toShort(),
                sessionId = sessionId,
                payload = chunkBytes,
            )
            broadcastToAllPeers(blePacket.serialize())
        }
    }

    private fun broadcastToAllPeers(data: ByteArray) {
        // Send via central connections
        connectedCentrals.values.forEach { gatt ->
            val service = gatt.getService(BleProtocol.SERVICE_UUID) ?: return@forEach
            val writeChar = service.getCharacteristic(BleProtocol.CHAR_WRITE_UUID) ?: return@forEach
            writeChar.value = data
            writeChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            gatt.writeCharacteristic(writeChar)
        }
        // Notify peripheral connections via GATT server
        connectedPeripherals.values.forEach { device ->
            val notifyChar = gattServer
                ?.getService(BleProtocol.SERVICE_UUID)
                ?.getCharacteristic(BleProtocol.CHAR_NOTIFY_UUID) ?: return@forEach
            notifyChar.value = data
            gattServer?.notifyCharacteristicChanged(device, notifyChar, false)
        }
    }

    // ─── Receive & Route ────────────────────────────────────────────────────

    private suspend fun handleIncomingData(fromAddress: String, data: ByteArray) {
        val packet = BlePacket.deserialize(data) ?: return
        val bufKey = "${fromAddress}_${packet.sessionId}"

        if (packet.totalChunks == 1.toShort()) {
            processReassembled(packet.payload, fromAddress)
        } else {
            val buffer = reassemblyBuffers.getOrPut(bufKey) { mutableListOf() }
            buffer.add(packet)
            if (buffer.size == packet.totalChunks.toInt()) {
                val fullPayload = buffer.sortedBy { it.chunkIndex }
                    .fold(byteArrayOf()) { acc, p -> acc + p.payload }
                reassemblyBuffers.remove(bufKey)
                processReassembled(fullPayload, fromAddress)
            }
        }
    }

    private suspend fun processReassembled(payload: ByteArray, fromAddress: String) {
        val meshPacket = MeshPacket.deserialize(payload) ?: return

        // Deduplicate
        if (!seenMessageIds.add(meshPacket.messageId)) return

        val myPubKey = Secp256k1.getXOnlyPublicKey(keyManager.getPrivateKeyBytes())

        // Verify signature
        val sigPayload = meshPacket.srcPubKey + meshPacket.dstPubKey +
            byteArrayOf(meshPacket.ttl) + sha256(meshPacket.ciphertext)
        if (!Secp256k1.schnorrVerify(sha256(sigPayload), meshPacket.signature, meshPacket.srcPubKey)) {
            return // Drop tampered packet
        }

        val isForMe = meshPacket.dstPubKey.contentEquals(myPubKey) ||
            meshPacket.dstPubKey.contentEquals(MeshPacket.BROADCAST_ADDR)

        if (isForMe) {
            _receivedPackets.emit(meshPacket)
        }

        // Forward if TTL > 0 and not reached destination
        if (meshPacket.ttl > 0 && !meshPacket.dstPubKey.contentEquals(myPubKey)) {
            val forwarded = meshPacket.copy(ttl = (meshPacket.ttl - 1).toByte())
            sendMeshPacket(forwarded)
        }
    }

    val connectedPeerCount: Int get() = connectedCentrals.size + connectedPeripherals.size
}

private class LruSet<T>(private val maxSize: Int) {
    private val map = object : LinkedHashMap<T, Unit>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(e: Map.Entry<T, Unit>) = size > maxSize
    }
    @Synchronized fun add(item: T): Boolean = map.put(item, Unit) == null
}
