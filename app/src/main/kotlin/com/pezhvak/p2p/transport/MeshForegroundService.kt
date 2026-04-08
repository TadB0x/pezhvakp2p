package com.pezhvak.p2p.transport

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pezhvak.p2p.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Keeps the mesh transports alive in the background.
 * Shown as a persistent notification so Android doesn't kill the process.
 */
@AndroidEntryPoint
class MeshForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "mesh_service"
        const val NOTIF_ID = 1001
        const val ACTION_STOP = "com.pezhvak.p2p.STOP_MESH"
    }

    @Inject lateinit var transportManager: TransportManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Mesh active"))
        transportManager.start()
    }

    override fun onDestroy() {
        transportManager.stop()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY  // Restart if killed
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(status: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, MeshForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle("Pezhvak")
            .setContentText(status)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Mesh Network",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps the mesh network running in background"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
