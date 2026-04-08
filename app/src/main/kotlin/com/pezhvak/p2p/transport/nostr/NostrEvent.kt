package com.pezhvak.p2p.transport.nostr

import com.pezhvak.p2p.core.crypto.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/**
 * Nostr protocol event (NIP-01).
 * https://github.com/nostr-protocol/nostr
 *
 * Event kinds used in Pezhvak:
 *   0   = metadata (profile)
 *   1   = text note (channel/forum post)
 *   3   = contact list
 *   4   = encrypted DM (NIP-04, legacy)
 *   44  = encrypted DM (NIP-44, preferred)
 *   40  = channel create
 *   41  = channel metadata
 *   42  = channel message
 *   43  = hide message
 *   44  = mute user (NOTE: conflict w/ NIP-44 – we use kind 10044 for mute)
 *   1000 = Pezhvak group create
 *   1001 = Pezhvak group message
 *   1002 = Pezhvak group key rotation
 *   1003 = Pezhvak forum thread
 *   1004 = Pezhvak forum reply
 *   1010 = Pezhvak news item (signed by trusted server key)
 *   9735 = zap (future)
 */
@Serializable
data class NostrEvent(
    val id: String,             // SHA-256 of serialized event (hex)
    val pubkey: String,         // author's x-only pubkey (hex)
    val created_at: Long,       // Unix timestamp (seconds)
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: String,            // 64-byte Schnorr sig (hex)
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Build and sign a new event.
         */
        fun build(
            privKeyHex: String,
            kind: Int,
            content: String,
            tags: List<List<String>> = emptyList(),
        ): NostrEvent {
            val privKey = privKeyHex.hexToByteArray()
            val pubKey = Secp256k1.getXOnlyPublicKey(privKey).toHexString()
            val createdAt = System.currentTimeMillis() / 1000L

            // Canonical serialization for ID computation
            val serialized = Json.encodeToString(
                buildJsonArray {
                    add(0)
                    add(pubKey)
                    add(createdAt)
                    add(kind)
                    add(buildJsonArray { tags.forEach { tag -> add(buildJsonArray { tag.forEach { add(it) } }) } })
                    add(content)
                }
            )
            val id = sha256(serialized.toByteArray(Charsets.UTF_8)).toHexString()
            val sig = Secp256k1.schnorrSign(id.hexToByteArray(), privKey).toHexString()

            return NostrEvent(
                id = id,
                pubkey = pubKey,
                created_at = createdAt,
                kind = kind,
                tags = tags,
                content = content,
                sig = sig
            )
        }

        fun fromJson(jsonStr: String): NostrEvent = json.decodeFromString(jsonStr)
    }

    fun toJson(): String = Json.encodeToString(this)

    /**
     * Verify the cryptographic integrity of this event.
     * Returns false if the ID or signature is invalid.
     */
    fun verify(): Boolean {
        // 1. Recompute ID
        val serialized = Json.encodeToString(
            buildJsonArray {
                add(0); add(pubkey); add(created_at); add(kind)
                add(buildJsonArray { tags.forEach { tag -> add(buildJsonArray { tag.forEach { add(it) } }) } })
                add(content)
            }
        )
        val expectedId = sha256(serialized.toByteArray(Charsets.UTF_8)).toHexString()
        if (expectedId != id) return false

        // 2. Verify Schnorr signature
        return Secp256k1.schnorrVerify(
            messageHash = id.hexToByteArray(),
            signature = sig.hexToByteArray(),
            publicKeyXOnly = pubkey.hexToByteArray()
        )
    }

    fun getTag(name: String): String? =
        tags.firstOrNull { it.firstOrNull() == name }?.getOrNull(1)

    fun getTags(name: String): List<String> =
        tags.filter { it.firstOrNull() == name }.mapNotNull { it.getOrNull(1) }

    /** Wire format: ["EVENT", <event>] for relay submission */
    fun toRelayMessage(): String = """["EVENT",${toJson()}]"""
}

object NostrWire {
    fun reqMessage(subscriptionId: String, vararg filters: JsonObject): String =
        """["REQ","$subscriptionId",${filters.joinToString(",") { it.toString() }}]"""

    fun closeMessage(subscriptionId: String): String =
        """["CLOSE","$subscriptionId"]"""
}

object NostrKind {
    const val METADATA = 0
    const val TEXT_NOTE = 1
    const val CONTACT_LIST = 3
    const val DM_LEGACY = 4
    const val DM_NIP44 = 14          // NIP-17 sealed DM
    const val CHANNEL_CREATE = 40
    const val CHANNEL_METADATA = 41
    const val CHANNEL_MESSAGE = 42
    const val GROUP_CREATE = 1000
    const val GROUP_MESSAGE = 1001
    const val GROUP_KEY_ROTATION = 1002
    const val FORUM_THREAD = 1003
    const val FORUM_REPLY = 1004
    const val NEWS_ITEM = 1010
    const val MESH_RELAY = 1100      // Used for carrying mesh packets over Nostr
    const val DELETE = 5
    const val REACTION = 7
    const val REPORT = 1984
}
