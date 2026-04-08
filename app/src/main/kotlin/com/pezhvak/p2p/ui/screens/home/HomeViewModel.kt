package com.pezhvak.p2p.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pezhvak.p2p.transport.TransportManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val totalPeers: Int = 0,
    val bleDevices: Int = 0,
    val wifiDirectConnected: Boolean = false,
    val nostrRelaysConnected: Int = 0,
    val myPubKeyHex: String = "",
    val displayName: String = "",
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val transportManager: TransportManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                transportManager.nostrRelayStatuses,
                flowOf(transportManager.bleConnectedPeers),
                flowOf(transportManager.isWifiDirectConnected),
            ) { nostrStatuses, blePeers, wifiDirect ->
                val nostrConnected = nostrStatuses.values.count {
                    it == com.pezhvak.p2p.transport.nostr.NostrClient.ConnectionState.CONNECTED
                }
                HomeUiState(
                    totalPeers = blePeers + nostrConnected + (if (wifiDirect) 1 else 0),
                    bleDevices = blePeers,
                    wifiDirectConnected = wifiDirect,
                    nostrRelaysConnected = nostrConnected,
                )
            }.collect { _uiState.value = it }
        }
    }
}
