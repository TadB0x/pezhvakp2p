package com.pezhvak.p2p.core.crypto

import org.bouncycastle.asn1.x9.X9ECParameters
import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger
import java.security.SecureRandom

/**
 * Secp256k1 keypair operations for Nostr-compatible identity.
 * All identity is based on this curve (same as Bitcoin/Nostr).
 */
object Secp256k1 {

    private val CURVE_PARAMS: X9ECParameters = CustomNamedCurves.getByName("secp256k1")
    val CURVE: ECDomainParameters = ECDomainParameters(
        CURVE_PARAMS.curve,
        CURVE_PARAMS.g,
        CURVE_PARAMS.n,
        CURVE_PARAMS.h
    )
    private val HALF_CURVE_ORDER: BigInteger = CURVE_PARAMS.n.shiftRight(1)
    private val secureRandom = SecureRandom()

    /** Generate a new random 32-byte private key */
    fun generatePrivateKey(): ByteArray {
        var key: ByteArray
        do {
            key = ByteArray(32)
            secureRandom.nextBytes(key)
        } while (!isValidPrivateKey(key))
        return key
    }

    fun isValidPrivateKey(key: ByteArray): Boolean {
        if (key.size != 32) return false
        val bi = BigInteger(1, key)
        return bi > BigInteger.ONE && bi < CURVE.n
    }

    /** Derive compressed 33-byte public key from private key */
    fun getPublicKey(privateKey: ByteArray): ByteArray {
        val privInt = BigInteger(1, privateKey)
        val pubPoint = CURVE.g.multiply(privInt).normalize()
        return pubPoint.getEncoded(true)  // compressed
    }

    /** Get the x-only 32-byte public key (Schnorr / Nostr style) */
    fun getXOnlyPublicKey(privateKey: ByteArray): ByteArray {
        return getPublicKey(privateKey).drop(1).toByteArray()
    }

    /** Sign a 32-byte message hash with Schnorr signature (64 bytes) */
    fun schnorrSign(messageHash: ByteArray, privateKey: ByteArray): ByteArray {
        require(messageHash.size == 32) { "Message hash must be 32 bytes" }
        // RFC 6979 deterministic k
        val privInt = BigInteger(1, privateKey)
        val k = generateDeterministicK(messageHash, privateKey)
        val R = CURVE.g.multiply(k).normalize()
        val rx = R.xCoord.encoded.takeLast(32).toByteArray()
        val e = BigInteger(1, sha256(rx + getXOnlyPublicKey(privateKey) + messageHash))
        val s = (k + e * privInt).mod(CURVE.n)
        val sig = ByteArray(64)
        val rBytes = rx
        val sBytes = s.toByteArray().let { if (it.size > 32) it.drop(1).toByteArray() else it }
        rBytes.copyInto(sig, 32 - rBytes.size)
        sBytes.copyInto(sig, 64 - sBytes.size)
        return sig
    }

    /** Verify a 64-byte Schnorr signature */
    fun schnorrVerify(messageHash: ByteArray, signature: ByteArray, publicKeyXOnly: ByteArray): Boolean {
        if (signature.size != 64 || publicKeyXOnly.size != 32) return false
        return try {
            val rx = signature.take(32).toByteArray()
            val s = BigInteger(1, signature.drop(32).toByteArray())
            val e = BigInteger(1, sha256(rx + publicKeyXOnly + messageHash))
            val P = liftX(publicKeyXOnly) ?: return false
            val sG = CURVE.g.multiply(s).normalize()
            val eP = P.multiply(e).normalize()
            val R = sG.add(eP.negate()).normalize()
            if (R.isInfinity) return false
            val Rx = R.xCoord.encoded.takeLast(32).toByteArray()
            Rx.contentEquals(rx)
        } catch (ex: Exception) {
            false
        }
    }

    /** ECDH shared secret for NIP-04/NIP-44 encryption */
    fun getSharedSecret(privateKey: ByteArray, publicKeyXOnly: ByteArray): ByteArray {
        val privInt = BigInteger(1, privateKey)
        val pubPoint = liftX(publicKeyXOnly) ?: throw IllegalArgumentException("Invalid pubkey")
        val shared = pubPoint.multiply(privInt).normalize()
        return shared.xCoord.encoded.takeLast(32).toByteArray()
    }

    private fun liftX(xBytes: ByteArray): ECPoint? {
        val x = BigInteger(1, xBytes)
        val p = CURVE.curve.field.characteristic
        val ySquared = (x.pow(3) + BigInteger.valueOf(7)).mod(p)
        val y = ySquared.modPow((p + BigInteger.ONE).divide(BigInteger.valueOf(4)), p)
        if (y.multiply(y).mod(p) != ySquared) return null
        val yFinal = if (y.testBit(0)) p - y else y
        val prefix = if (yFinal.testBit(0)) "03" else "02"
        return CURVE.curve.decodePoint(
            (prefix + xBytes.toHexString()).hexToByteArray()
        )
    }

    private fun generateDeterministicK(hash: ByteArray, privKey: ByteArray): BigInteger {
        // RFC 6979
        var v = ByteArray(32) { 0x01 }
        var k = ByteArray(32) { 0x00 }
        k = hmacSha256(k, v + byteArrayOf(0x00) + privKey + hash)
        v = hmacSha256(k, v)
        k = hmacSha256(k, v + byteArrayOf(0x01) + privKey + hash)
        v = hmacSha256(k, v)
        while (true) {
            v = hmacSha256(k, v)
            val candidate = BigInteger(1, v)
            if (candidate >= BigInteger.ONE && candidate < CURVE.n) return candidate
            k = hmacSha256(k, v + byteArrayOf(0x00))
            v = hmacSha256(k, v)
        }
    }
}
