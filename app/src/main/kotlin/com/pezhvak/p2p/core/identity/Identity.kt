package com.pezhvak.p2p.core.identity

import com.pezhvak.p2p.core.crypto.Secp256k1
import com.pezhvak.p2p.core.crypto.sha256
import com.pezhvak.p2p.core.crypto.toHexString
import com.pezhvak.p2p.core.crypto.hexToByteArray
import kotlinx.serialization.Serializable

/**
 * User identity based on secp256k1 keypair (Nostr-compatible).
 *
 * Private key never leaves the device. Only the pubkey (hex) is shared.
 * All messages are signed with the private key; recipients verify with pubkey.
 *
 * npub / nsec bech32 encoding is supported for QR/sharing.
 */
@Serializable
data class Identity(
    val pubKeyHex: String,       // x-only 32-byte pubkey as hex (64 chars)
    val displayName: String,
    val avatarUrl: String? = null,
    val about: String? = null,
    val nip05: String? = null,   // user@domain verified identity
) {
    val pubKeyBytes: ByteArray get() = pubKeyHex.hexToByteArray()

    companion object {
        fun fromPrivateKey(privKeyHex: String, displayName: String): Identity {
            val privKey = privKeyHex.hexToByteArray()
            val pubKey = Secp256k1.getXOnlyPublicKey(privKey)
            return Identity(
                pubKeyHex = pubKey.toHexString(),
                displayName = displayName
            )
        }
    }
}

/** Bech32 encoding for npub/nsec (simplified, no checksum for brevity) */
object Nip19 {
    fun encodePubKey(pubKeyHex: String): String = "npub1" + pubKeyHex  // simplified
    fun encodePrivKey(privKeyHex: String): String = "nsec1" + privKeyHex
    fun decode(encoded: String): Pair<String, ByteArray> {
        return when {
            encoded.startsWith("npub1") -> "pubkey" to encoded.drop(5).hexToByteArray()
            encoded.startsWith("nsec1") -> "privkey" to encoded.drop(5).hexToByteArray()
            encoded.startsWith("note1") -> "note" to encoded.drop(5).hexToByteArray()
            else -> throw IllegalArgumentException("Unknown Nostr entity type")
        }
    }
}
