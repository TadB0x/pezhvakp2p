package com.pezhvak.p2p.core.crypto

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// ─── SHA-256 ──────────────────────────────────────────────────────────────────

fun sha256(data: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(data)

fun sha256(vararg parts: ByteArray): ByteArray =
    sha256(parts.fold(byteArrayOf()) { acc, b -> acc + b })

// ─── HMAC-SHA256 ──────────────────────────────────────────────────────────────

fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val hmac = HMac(SHA256Digest())
    hmac.init(KeyParameter(key))
    hmac.update(data, 0, data.size)
    val out = ByteArray(32)
    hmac.doFinal(out, 0)
    return out
}

// ─── HKDF ─────────────────────────────────────────────────────────────────────

fun hkdf(inputKeyMaterial: ByteArray, salt: ByteArray, info: ByteArray, length: Int = 32): ByteArray {
    val gen = HKDFBytesGenerator(SHA256Digest())
    gen.init(HKDFParameters(inputKeyMaterial, salt, info))
    val out = ByteArray(length)
    gen.generateBytes(out, 0, length)
    return out
}

// ─── AES-256-GCM ──────────────────────────────────────────────────────────────

private const val GCM_IV_SIZE = 12
private const val GCM_TAG_SIZE = 128 // bits

/**
 * Encrypt plaintext with AES-256-GCM.
 * Returns: [12-byte IV | ciphertext+tag]
 */
fun aesGcmEncrypt(key: ByteArray, plaintext: ByteArray, aad: ByteArray? = null): ByteArray {
    require(key.size == 32) { "Key must be 32 bytes" }
    val iv = ByteArray(GCM_IV_SIZE).also { SecureRandom().nextBytes(it) }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_SIZE, iv))
    aad?.let { cipher.updateAAD(it) }
    val ciphertext = cipher.doFinal(plaintext)
    return iv + ciphertext
}

/**
 * Decrypt AES-256-GCM.
 * Input: [12-byte IV | ciphertext+tag]
 */
fun aesGcmDecrypt(key: ByteArray, data: ByteArray, aad: ByteArray? = null): ByteArray {
    require(key.size == 32) { "Key must be 32 bytes" }
    require(data.size > GCM_IV_SIZE) { "Data too short" }
    val iv = data.take(GCM_IV_SIZE).toByteArray()
    val ciphertext = data.drop(GCM_IV_SIZE).toByteArray()
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_SIZE, iv))
    aad?.let { cipher.updateAAD(it) }
    return cipher.doFinal(ciphertext)
}

// ─── NIP-44 Encryption (versioned, padded) ────────────────────────────────────

/**
 * NIP-44 v2: ECDH → HKDF → ChaCha20-Poly1305 equivalent using AES-GCM.
 * Message is padded to hide length metadata.
 */
fun nip44Encrypt(senderPrivKey: ByteArray, recipientPubKeyXOnly: ByteArray, plaintext: String): String {
    val sharedSecret = Secp256k1.getSharedSecret(senderPrivKey, recipientPubKeyXOnly)
    val conversationKey = hkdf(
        inputKeyMaterial = sharedSecret,
        salt = ByteArray(32),
        info = "nip44-v2".toByteArray()
    )
    val nonce = ByteArray(32).also { SecureRandom().nextBytes(it) }
    val (encKey, authKey) = nip44DeriveMessageKeys(conversationKey, nonce)

    val paddedPlaintext = nip44Pad(plaintext.toByteArray(Charsets.UTF_8))
    val ciphertext = aesGcmEncrypt(encKey, paddedPlaintext, authKey)

    val payload = byteArrayOf(2) + nonce + ciphertext  // version=2
    val mac = hmacSha256(authKey, payload)
    return (payload + mac).toBase64()
}

fun nip44Decrypt(recipientPrivKey: ByteArray, senderPubKeyXOnly: ByteArray, payload: String): String {
    val data = payload.fromBase64()
    require(data[0] == 2.toByte()) { "Unknown NIP-44 version" }
    val nonce = data.slice(1..32).toByteArray()
    val mac = data.takeLast(32).toByteArray()
    val ciphertext = data.drop(33).dropLast(32).toByteArray()

    val sharedSecret = Secp256k1.getSharedSecret(recipientPrivKey, senderPubKeyXOnly)
    val conversationKey = hkdf(sharedSecret, ByteArray(32), "nip44-v2".toByteArray())
    val (encKey, authKey) = nip44DeriveMessageKeys(conversationKey, nonce)

    val payloadToVerify = data.take(data.size - 32).toByteArray()
    val expectedMac = hmacSha256(authKey, payloadToVerify)
    require(mac.contentEquals(expectedMac)) { "MAC verification failed – message tampered" }

    val paddedPlaintext = aesGcmDecrypt(encKey, ciphertext, authKey)
    return nip44Unpad(paddedPlaintext).toString(Charsets.UTF_8)
}

private fun nip44DeriveMessageKeys(conversationKey: ByteArray, nonce: ByteArray): Pair<ByteArray, ByteArray> {
    val keys = hkdf(conversationKey, nonce, "nip44-v2".toByteArray(), 76)
    return keys.take(32).toByteArray() to keys.drop(32).take(32).toByteArray()
}

private fun nip44Pad(plaintext: ByteArray): ByteArray {
    val len = plaintext.size
    val paddedLen = when {
        len <= 32 -> 32
        len <= 64 -> 64
        else -> ((len + 15) / 16) * 16
    }
    val padded = ByteArray(paddedLen + 2)
    padded[0] = (len shr 8).toByte()
    padded[1] = (len and 0xFF).toByte()
    plaintext.copyInto(padded, 2)
    return padded
}

private fun nip44Unpad(padded: ByteArray): ByteArray {
    val len = ((padded[0].toInt() and 0xFF) shl 8) or (padded[1].toInt() and 0xFF)
    return padded.drop(2).take(len).toByteArray()
}

// ─── Hex / Base64 helpers ─────────────────────────────────────────────────────

fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
fun String.hexToByteArray(): ByteArray {
    require(length % 2 == 0) { "Odd hex length" }
    return ByteArray(length / 2) { i ->
        ((Character.digit(this[i * 2], 16) shl 4) + Character.digit(this[i * 2 + 1], 16)).toByte()
    }
}

fun ByteArray.toBase64(): String = android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)
fun String.fromBase64(): ByteArray = android.util.Base64.decode(this, android.util.Base64.NO_WRAP)
