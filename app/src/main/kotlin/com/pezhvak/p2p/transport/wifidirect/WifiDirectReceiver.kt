package com.pezhvak.p2p.transport.wifidirect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pManager

/**
 * Receives WiFi Direct system broadcasts and dispatches to WifiDirectAdapter.
 * Must be registered/unregistered with the Activity/Service lifecycle.
 */
class WifiDirectReceiver : BroadcastReceiver() {
    var adapter: WifiDirectAdapter? = null
    private var manager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null

    fun init(adapter: WifiDirectAdapter, manager: WifiP2pManager, channel: WifiP2pManager.Channel) {
        this.adapter = adapter
        this.manager = manager
        this.channel = channel
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                // WiFi Direct enabled/disabled
            }
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                manager?.requestPeers(channel) { peerList ->
                    adapter?.updatePeerList(peerList)
                }
            }
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                manager?.requestConnectionInfo(channel) { info ->
                    adapter?.updateConnectionInfo(info)
                }
            }
            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                // Our device's WiFi Direct status changed
            }
        }
    }
}
