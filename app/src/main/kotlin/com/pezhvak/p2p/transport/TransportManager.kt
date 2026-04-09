package com.pezhvak.p2p.transport

import com.pezhvak.p2p.core.crypto.*
import com.pezhvak.p2p.core.db.dao.MessageDao
import com.pezhvak.p2p.core.db.entities.ConversationType
import com.pezhvak.p2p.core.db.entities.DeliveryStatus
import com.pezhvak.p2p.core.db.entities.MessageEntity
import com.pezhvak.p2p.core.db.entities.TransportSource
import com.pezhvak.p2p.core.identity.KeyManager
import com.pezhvak.p2p.transport.ble.BleMeshAdapter
import com.pezhvak.p2p.transport.ble.BleProtocol
import com.pezhvak.p2p.transport.ble.BleProtocol.MeshPacket
import com.pezhvak.p2p.transport.nostr.NostrEvent
import com.pezhvak.p2p.transport.nostr.NostrKind
import com.pezhvak.p2p.transport.nostr.NostrRelayManager
import com.pezhvak.p2p.transport.wifidirect.WifiDirectAdapter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified transport facade.
 * Sends via all available transports; deduplicates received messages.
 * Persists all sent/received messages to the local Room database.
 */
@Singleton
class TransportManager @Inject constructor(
    private val nostrRelayManager: NostrRelayManager,
    private val bleMeshAdapter: BleMeshAdapter,
    private val wifiDirectAdapter: WifiDirectAdapter,
    private val keyManager: KeyManager,
    private val messageDao: MessageDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val seenEventIds = LruSet<String>(maxSize = 50_000)

    data class IncomingMessage(
        val eventId: String,
        val senderPubKey: String,
        val encryptedContent: String,
        val kind: Int,
        val tags: List<List<String>>,
        val createdAt: Long,
        val transport: TransportSource,
        val nostrEvent: NostrEvent?,
    )

    private val _messages = MutableSharedFlow<IncomingMessage>(extraBufferCapacity = 1024)
    val messages: SharedFlow<IncomingMessage> = _messages.asSharedFlow()

    // ─── Lifecycle ──────────────────────────────────────────────────────────

    fun start() {
        nostrRelayManager.start()
        bleMeshAdapter.start()
        listenAllTransports()
    }

    fun stop() {
        nostrRelayManager.stop()
        bleMeshAdapter.stop()
        wifiDirectAdapter.disconnect()
        scope.cancel()
    }

    private fun listenAllTransports() {
        // Nostr relay events → persist incoming channel/forum messages
        scope.launch {
            nostrRelayManager.events.collect { event ->
                if (seenEventIds.add(event.id)) {
                    val msg = IncomingMessage(
                        eventId = event.id,
                        senderPubKey = event.pubkey,
                        encryptedContent = event.content,
                        kind = event.kind,
                        tags = event.tags,
                        createdAt = event.created_at * 1000L,
                        transport = TransportSource.NOSTR,
                        nostrEvent = event,
                    )
                    _messages.emit(msg)
                    persistIncomingIfPublic(msg)
                }
            }
        }

        // BLE Mesh packets
        scope.launch {
            bleMeshAdapter.receivedPackets.collect { meshPacket ->
                val msgId = meshPacket.messageId.toString(16)
                if (seenEventIds.add(msgId)) {
                    try {
                        val myPrivKey = keyManager.getPrivateKeyBytes()
                        val plaintext = nip44Decrypt(
                            recipientPrivKey = myPrivKey,
                            senderPubKeyXOnly = meshPacket.srcPubKey,
                            payload = meshPacket.ciphertext.toBase64(),
                        )
                        val inner = Json.parseToJsonElement(plaintext).jsonObject
                        _messages.emit(IncomingMessage(
                            eventId = msgId,
                            senderPubKey = meshPacket.srcPubKey.toHexString(),
                            encryptedContent = plaintext,
                            kind = inner["kind"]?.jsonPrimitive?.int ?: -1,
                            tags = emptyList(),
                            createdAt = System.currentTimeMillis(),
                            transport = TransportSource.BLE_MESH,
                            nostrEvent = null,
                        ))
                    } catch (_: Exception) { }
                }
            }
        }

        // WiFi Direct messages
        scope.launch {
            wifiDirectAdapter.receivedMessages.collect { msg ->
                val msgId = sha256(msg.ciphertext).toHexString()
                if (seenEventIds.add(msgId)) {
                    _messages.emit(IncomingMessage(
                        eventId = msgId,
                        senderPubKey = msg.senderPubKey,
                        encryptedContent = msg.ciphertext.toBase64(),
                        kind = -1,
                        tags = emptyList(),
                        createdAt = msg.timestamp,
                        transport = TransportSource.WIFI_DIRECT,
                        nostrEvent = null,
                    ))
                }
            }
        }
    }

    /** Persist channel/forum messages that arrive from the network */
    private suspend fun persistIncomingIfPublic(msg: IncomingMessage) {
        val conversationId = when (msg.kind) {
            NostrKind.CHANNEL_MESSAGE, NostrKind.FORUM_REPLY -> {
                msg.tags.firstOrNull { it.getOrNull(0) == "e" }?.getOrNull(1) ?: return
            }
            else -> return
        }
        val type = if (msg.kind == NostrKind.FORUM_REPLY) ConversationType.FORUM else ConversationType.CHANNEL
        val entity = MessageEntity(
            eventId = msg.eventId,
            conversationId = conversationId,
            conversationType = type,
            senderPubKey = msg.senderPubKey,
            senderSig = "",
            contentEncrypted = null,
            contentPlain = msg.encryptedContent,
            contentHash = sha256(msg.encryptedContent.toByteArray()).toHexString(),
            replyToEventId = msg.tags.firstOrNull { it.getOrNull(0) == "e" && it.getOrNull(3) == "reply" }?.getOrNull(1),
            mediaAttachments = null,
            reactions = null,
            createdAt = msg.createdAt,
            receivedAt = System.currentTimeMillis(),
            deliveryStatus = DeliveryStatus.DELIVERED,
            transportSource = msg.transport,
            seenByPeerAt = null,
            threadId = if (type == ConversationType.FORUM) conversationId else null,
        )
        messageDao.insert(entity)
    }

    // ─── Send ────────────────────────────────────────────────────────────────

    suspend fun sendDirectMessage(
        recipientPubKeyHex: String,
        content: String,
        kind: Int = NostrKind.DM_NIP44,
    ): String {
        val privKey = keyManager.getPrivateKeyBytes()
        val recipientPubKey = recipientPubKeyHex.hexToByteArray()
        val encrypted = nip44Encrypt(privKey, recipientPubKey, content)

        val nostrEvent = NostrEvent.build(
            privKeyHex = keyManager.getPrivateKeyHex(),
            kind = kind,
            content = encrypted,
            tags = listOf(listOf("p", recipientPubKeyHex)),
        )
        scope.launch { nostrRelayManager.publish(nostrEvent) }
        sendViaBle(privKey, recipientPubKey, content, nostrEvent.id)
        return nostrEvent.id
    }

    suspend fun broadcastChannelMessage(
        channelId: String,
        content: String,
        kind: Int = NostrKind.CHANNEL_MESSAGE,
    ): NostrEvent {
        val myPubKey = keyManager.getOrCreateIdentity().pubKeyHex
        val event = NostrEvent.build(
            privKeyHex = keyManager.getPrivateKeyHex(),
            kind = kind,
            content = content,
            tags = listOf(listOf("e", channelId, "", "root"), listOf("channel_id", channelId)),
        )

        // Persist locally first so UI updates immediately
        val type = if (kind == NostrKind.FORUM_REPLY) ConversationType.FORUM else ConversationType.CHANNEL
        val entity = MessageEntity(
            eventId = event.id,
            conversationId = channelId,
            conversationType = type,
            senderPubKey = myPubKey,
            senderSig = event.sig,
            contentEncrypted = null,
            contentPlain = content,
            contentHash = sha256(content.toByteArray()).toHexString(),
            replyToEventId = null,
            mediaAttachments = null,
            reactions = null,
            createdAt = event.created_at * 1000L,
            receivedAt = System.currentTimeMillis(),
            deliveryStatus = DeliveryStatus.SENDING,
            transportSource = TransportSource.NOSTR,
            seenByPeerAt = null,
            threadId = if (type == ConversationType.FORUM) channelId else null,
        )
        messageDao.insert(entity)

        // Broadcast
        scope.launch {
            nostrRelayManager.publish(event)
            messageDao.updateStatus(event.id, DeliveryStatus.SENT)
        }
        broadcastViaBle(content, event.id)
        return event
    }

    private fun jsonEscapeString(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

    private fun sendViaBle(privKey: ByteArray, recipientPubKey: ByteArray, content: String, eventId: String) {
        val envelope = """{"kind":1001,"content":"${jsonEscapeString(content)}","id":"$eventId"}"""
        val ciphertext = nip44Encrypt(privKey, recipientPubKey, envelope).fromBase64()
        val msgId = java.util.Random().nextLong()
        val sigPayload = Secp256k1.getXOnlyPublicKey(privKey) + recipientPubKey +
            byteArrayOf(BleProtocol.MAX_HOP_COUNT) + sha256(ciphertext)
        val sig = Secp256k1.schnorrSign(sha256(sigPayload), privKey)
        bleMeshAdapter.sendMeshPacket(MeshPacket(
            srcPubKey = Secp256k1.getXOnlyPublicKey(privKey),
            dstPubKey = recipientPubKey,
            ttl = BleProtocol.MAX_HOP_COUNT,
            messageId = msgId,
            ciphertext = ciphertext,
            signature = sig,
        ))
    }

    private fun broadcastViaBle(content: String, eventId: String) {
        val privKey = keyManager.getPrivateKeyBytes()
        val envelope = """{"kind":1001,"content":"${jsonEscapeString(content)}","id":"$eventId"}"""
        val broadcastKey = sha256(eventId.toByteArray())
        val ciphertext = aesGcmEncrypt(broadcastKey, envelope.toByteArray())
        val msgId = java.util.Random().nextLong()
        val sigPayload = Secp256k1.getXOnlyPublicKey(privKey) +
            MeshPacket.BROADCAST_ADDR + byteArrayOf(BleProtocol.MAX_HOP_COUNT) + sha256(ciphertext)
        val sig = Secp256k1.schnorrSign(sha256(sigPayload), privKey)
        bleMeshAdapter.sendMeshPacket(MeshPacket(
            srcPubKey = Secp256k1.getXOnlyPublicKey(privKey),
            dstPubKey = MeshPacket.BROADCAST_ADDR,
            ttl = BleProtocol.MAX_HOP_COUNT,
            messageId = msgId,
            ciphertext = ciphertext,
            signature = sig,
        ))
    }

    fun subscribeToChannel(channelId: String): Flow<IncomingMessage> {
        val filter = buildJsonObject {
            putJsonArray("kinds") { add(NostrKind.CHANNEL_MESSAGE) }
            putJsonArray("#e") { add(channelId) }
        }
        nostrRelayManager.subscribe("channel_$channelId", filter)
        return messages.filter { msg ->
            msg.tags.any { it.getOrNull(0) == "e" && it.getOrNull(1) == channelId }
        }
    }

    val bleConnectedPeers: Int get() = bleMeshAdapter.connectedPeerCount
    val isWifiDirectConnected: Boolean get() = wifiDirectAdapter.isConnected
    val nostrRelayStatuses get() = nostrRelayManager.relayStatuses
}

private class LruSet<T>(private val maxSize: Int) {
    private val map = object : LinkedHashMap<T, Unit>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(e: Map.Entry<T, Unit>) = size > maxSize
    }
    @Synchronized fun add(item: T): Boolean = map.put(item, Unit) == null
}
