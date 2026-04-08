package com.pezhvak.p2p.calls

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pezhvak.p2p.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service for active voice/video calls.
 * Keeps camera/mic active when the app is backgrounded.
 * Foreground type: microphone | camera (declared in manifest).
 */
@AndroidEntryPoint
class CallService : Service() {

    companion object {
        const val CHANNEL_ID = "call_service"
        const val NOTIF_ID = 1002
        const val EXTRA_PEER_ID = "peer_id"
        const val EXTRA_HAS_VIDEO = "has_video"
    }

    @Inject lateinit var callManager: CallManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val peerId = intent?.getStringExtra(EXTRA_PEER_ID) ?: return START_NOT_STICKY
        val hasVideo = intent.getBooleanExtra(EXTRA_HAS_VIDEO, false)
        startForeground(NOTIF_ID, buildNotification(peerId, hasVideo))
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        callManager.endCall()
        super.onDestroy()
    }

    private fun buildNotification(peerId: String, hasVideo: Boolean): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(if (hasVideo) "Video call" else "Voice call")
            .setContentText("In call with ${peerId.take(12)}…")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Active Call",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Active voice/video call" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
