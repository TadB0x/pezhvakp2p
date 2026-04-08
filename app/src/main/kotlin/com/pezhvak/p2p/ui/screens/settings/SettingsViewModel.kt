package com.pezhvak.p2p.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pezhvak.p2p.core.identity.KeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    private val keyManager: KeyManager,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        val identity = keyManager.getOrCreateIdentity()
        _state.update { it.copy(displayName = identity.displayName, pubKeyHex = identity.pubKeyHex) }
    }

    fun setBleMeshEnabled(enabled: Boolean) = _state.update { it.copy(bleEnabled = enabled) }
    fun setWifiDirectEnabled(enabled: Boolean) = _state.update { it.copy(wifiDirectEnabled = enabled) }
    fun setReadReceipts(enabled: Boolean) = _state.update { it.copy(readReceiptsEnabled = enabled) }
    fun setShowOnlineStatus(enabled: Boolean) = _state.update { it.copy(showOnlineStatus = enabled) }
    fun setDarkMode(enabled: Boolean) = _state.update { it.copy(darkMode = enabled) }
    fun setNotifications(enabled: Boolean) = _state.update { it.copy(notificationsEnabled = enabled) }
    fun exportKey() { /* trigger key export flow */ }
}
