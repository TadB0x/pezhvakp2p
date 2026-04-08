package com.pezhvak.p2p.core.identity

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.pezhvak.p2p.core.crypto.Secp256k1
import com.pezhvak.p2p.core.crypto.toHexString
import com.pezhvak.p2p.core.crypto.hexToByteArray
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the local secp256k1 keypair.
 * The private key is stored in EncryptedSharedPreferences (hardware-backed
 * keystore on supported devices, AES-256-GCM encrypted on others).
 *
 * The key is NEVER exported unless the user explicitly backs it up.
 */
@Singleton
class KeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_FILE = "pezhvak_keys"
        private const val KEY_PRIVATE = "private_key_hex"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_ABOUT = "about"
        private const val KEY_NIP05 = "nip05"
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    val hasIdentity: Boolean get() = prefs.contains(KEY_PRIVATE)

    /** Create a new identity, or return existing one */
    fun getOrCreateIdentity(displayName: String = "Anon"): Identity {
        val privKeyHex = prefs.getString(KEY_PRIVATE, null)
            ?: generateAndStore(displayName)
        val privKey = privKeyHex.hexToByteArray()
        val pubKey = Secp256k1.getXOnlyPublicKey(privKey)
        return Identity(
            pubKeyHex = pubKey.toHexString(),
            displayName = prefs.getString(KEY_DISPLAY_NAME, displayName) ?: displayName,
            about = prefs.getString(KEY_ABOUT, null),
            nip05 = prefs.getString(KEY_NIP05, null)
        )
    }

    fun getPrivateKeyHex(): String =
        prefs.getString(KEY_PRIVATE, null) ?: error("No identity found")

    fun getPrivateKeyBytes(): ByteArray = getPrivateKeyHex().hexToByteArray()

    fun updateProfile(displayName: String? = null, about: String? = null, nip05: String? = null) {
        prefs.edit().apply {
            displayName?.let { putString(KEY_DISPLAY_NAME, it) }
            about?.let { putString(KEY_ABOUT, it) }
            nip05?.let { putString(KEY_NIP05, it) }
            apply()
        }
    }

    /**
     * Import an existing nsec/hex private key.
     * Used when restoring an account.
     */
    fun importPrivateKey(privKeyHex: String, displayName: String) {
        require(Secp256k1.isValidPrivateKey(privKeyHex.hexToByteArray())) {
            "Invalid private key"
        }
        prefs.edit().apply {
            putString(KEY_PRIVATE, privKeyHex)
            putString(KEY_DISPLAY_NAME, displayName)
            apply()
        }
    }

    /**
     * Export the private key hex for backup.
     * Only call this after explicit user confirmation + authentication.
     */
    fun exportPrivateKey(): String = getPrivateKeyHex()

    private fun generateAndStore(displayName: String): String {
        val privKey = Secp256k1.generatePrivateKey()
        val hex = privKey.toHexString()
        prefs.edit().apply {
            putString(KEY_PRIVATE, hex)
            putString(KEY_DISPLAY_NAME, displayName)
            apply()
        }
        return hex
    }
}
