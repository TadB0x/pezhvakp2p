package com.pezhvak.p2p.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import com.pezhvak.p2p.core.identity.KeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class SettingsState(
    val displayName: String = "",
    val pubKeyHex: String = "",
    val bleEnabled: Boolean = true,
    val wifiDirectEnabled: Boolean = true,
    val relayCount: Int = 6,
    val readReceiptsEnabled: Boolean = true,
    val showOnlineStatus: Boolean = true,
    val blockedCount: Int = 0,
    val darkMode: Boolean = true,
    val notificationsEnabled: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyManager: KeyManager,
) : ViewModel() {

    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        SettingsState(
            bleEnabled = prefs.getBoolean("bleEnabled", true),
            wifiDirectEnabled = prefs.getBoolean("wifiDirectEnabled", true),
            readReceiptsEnabled = prefs.getBoolean("readReceiptsEnabled", true),
            showOnlineStatus = prefs.getBoolean("showOnlineStatus", true),
            darkMode = prefs.getBoolean("darkMode", true),
            notificationsEnabled = prefs.getBoolean("notificationsEnabled", true)
        )
    )
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        val identity = keyManager.getOrCreateIdentity()
        _state.update { it.copy(displayName = identity.displayName, pubKeyHex = identity.pubKeyHex) }
    }

    fun setBleMeshEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("bleEnabled", enabled).apply()
        _state.update { it.copy(bleEnabled = enabled) }
    }
    fun setWifiDirectEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("wifiDirectEnabled", enabled).apply()
        _state.update { it.copy(wifiDirectEnabled = enabled) }
    }
    fun setReadReceipts(enabled: Boolean) {
        prefs.edit().putBoolean("readReceiptsEnabled", enabled).apply()
        _state.update { it.copy(readReceiptsEnabled = enabled) }
    }
    fun setShowOnlineStatus(enabled: Boolean) {
        prefs.edit().putBoolean("showOnlineStatus", enabled).apply()
        _state.update { it.copy(showOnlineStatus = enabled) }
    }
    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean("darkMode", enabled).apply()
        _state.update { it.copy(darkMode = enabled) }
    }
    fun setNotifications(enabled: Boolean) {
        prefs.edit().putBoolean("notificationsEnabled", enabled).apply()
        _state.update { it.copy(notificationsEnabled = enabled) }
    }
    fun exportKey() { /* trigger key export flow */ }
}
