package com.pezhvak.p2p.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.pezhvak.p2p.transport.MeshForegroundService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val serviceIntent = Intent(context, MeshForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
