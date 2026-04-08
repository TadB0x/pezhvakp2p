package com.pezhvak.p2p.transport.ble

import java.util.UUID

/**
 * BLE GATT service/characteristic UUIDs and protocol constants for Pezhvak mesh.
 *
 * Packet structure (MTU-chunked, max 512 bytes per write):
 * ┌────────┬────────┬──────────┬──────────────────────────────────┐
 * │ 1B ver │ 1B type│ 2B chunk │ N bytes payload                  │
 * └────────┴────────┴──────────┴──────────────────────────────────┘
 *
 * Multi-chunk reassembly: first packet carries total_chunks count.
 * Each packet is individually signed for integrity.
 */
object BleProtocol {

    // Service UUID – registered Pezhvak service (random UUID, hardcoded in app)
    val SERVICE_UUID: UUID = UUID.fromString("00002024-0000-1000-8000-00805f9b34fb")

    // Characteristics
    val CHAR_WRITE_UUID: UUID = UUID.fromString("00002025-0000-1000-8000-00805f9b34fb")   // Client → Server
    val CHAR_NOTIFY_UUID: UUID = UUID.fromString("00002026-0000-1000-8000-00805f9b34fb")  // Server → Client
    val CHAR_IDENTITY_UUID: UUID = UUID.fromString("00002027-0000-1000-8000-00805f9b34fb") // Public identity info

    // Descriptor for enabling notifications
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val PROTOCOL_VERSION: Byte = 1

    // Packet types
    const val TYPE_HANDSHAKE: Byte = 0x01
    const val TYPE_MESSAGE: Byte = 0x02
    const val TYPE_ACK: Byte = 0x03
    const val TYPE_ROUTE_DISCOVERY: Byte = 0x04
    const val TYPE_ROUTE_REPLY: Byte = 0x05
    const val TYPE_HEARTBEAT: Byte = 0x06
    const val TYPE_MESH_RELAY: Byte = 0x07       // Relaying packet for another node
    const val TYPE_DISCONNECT: Byte = 0x08
    const val TYPE_FILE_CHUNK: Byte = 0x09

    const val MAX_MTU = 512
    const val PREFERRED_MTU = 512
    const val HEADER_SIZE = 8    // ver(1) + type(1) + chunk_idx(2) + total_chunks(2) + session_id(2)
    const val MAX_PAYLOAD = MAX_MTU - HEADER_SIZE

    // Mesh routing
    const val MAX_HOP_COUNT: Byte = 7
    const val MESH_TTL_MS = 30_000L
    const val HANDSHAKE_TIMEOUT_MS = 10_000L

    data class BlePacket(
        val version: Byte = PROTOCOL_VERSION,
        val type: Byte,
        val chunkIndex: Short,
        val totalChunks: Short,
        val sessionId: Short,
        val payload: ByteArray,
    ) {
        fun serialize(): ByteArray {
            val buf = ByteArray(HEADER_SIZE + payload.size)
            buf[0] = version
            buf[1] = type
            buf[2] = (chunkIndex.toInt() shr 8).toByte()
            buf[3] = (chunkIndex.toInt() and 0xFF).toByte()
            buf[4] = (totalChunks.toInt() shr 8).toByte()
            buf[5] = (totalChunks.toInt() and 0xFF).toByte()
            buf[6] = (sessionId.toInt() shr 8).toByte()
            buf[7] = (sessionId.toInt() and 0xFF).toByte()
            payload.copyInto(buf, HEADER_SIZE)
            return buf
        }

        companion object {
            fun deserialize(data: ByteArray): BlePacket? {
                if (data.size < HEADER_SIZE) return null
                return BlePacket(
                    version = data[0],
                    type = data[1],
                    chunkIndex = ((data[2].toInt() and 0xFF shl 8) or (data[3].toInt() and 0xFF)).toShort(),
                    totalChunks = ((data[4].toInt() and 0xFF shl 8) or (data[5].toInt() and 0xFF)).toShort(),
                    sessionId = ((data[6].toInt() and 0xFF shl 8) or (data[7].toInt() and 0xFF)).toShort(),
                    payload = data.drop(HEADER_SIZE).toByteArray()
                )
            }
        }
    }

    /**
     * Mesh packet header embedded in TYPE_MESSAGE / TYPE_MESH_RELAY payloads.
     *
     * ┌─────────────┬──────────────┬────────┬─────────┬──────────┐
     * │ 32B src_pub │ 32B dst_pub  │ 1B ttl │ 8B msgId│ N cipher │
     * └─────────────┴──────────────┴────────┴─────────┴──────────┘
     */
    data class MeshPacket(
        val srcPubKey: ByteArray,   // 32 bytes x-only
        val dstPubKey: ByteArray,   // 32 bytes x-only, or FF*32 = broadcast
        val ttl: Byte,
        val messageId: Long,        // 8 bytes, random – prevents re-routing loops
        val ciphertext: ByteArray,  // NIP-44 encrypted payload
        val signature: ByteArray,   // 64-byte Schnorr sig over (src+dst+ttl+msgId+hash(cipher))
    ) {
        companion object {
            val BROADCAST_ADDR = ByteArray(32) { 0xFF.toByte() }

            fun serialize(p: MeshPacket): ByteArray {
                val idBytes = ByteArray(8).also {
                    for (i in 0..7) it[i] = (p.messageId shr (56 - i * 8)).toByte()
                }
                return p.srcPubKey + p.dstPubKey + byteArrayOf(p.ttl) + idBytes + p.ciphertext + p.signature
            }

            fun deserialize(data: ByteArray): MeshPacket? {
                if (data.size < 32 + 32 + 1 + 8 + 64) return null
                var offset = 0
                val src = data.slice(offset until offset + 32).toByteArray(); offset += 32
                val dst = data.slice(offset until offset + 32).toByteArray(); offset += 32
                val ttl = data[offset++]
                var msgId = 0L
                for (i in 0..7) msgId = (msgId shl 8) or (data[offset++].toLong() and 0xFF)
                val cipherLen = data.size - offset - 64
                if (cipherLen < 0) return null
                val cipher = data.slice(offset until offset + cipherLen).toByteArray(); offset += cipherLen
                val sig = data.slice(offset until offset + 64).toByteArray()
                return MeshPacket(src, dst, ttl, msgId, cipher, sig)
            }
        }
    }
}
