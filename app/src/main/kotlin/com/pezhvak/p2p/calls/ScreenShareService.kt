package com.pezhvak.p2p.calls

import android.app.*
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service required for MediaProjection screen capture API.
 * Foreground type: mediaProjection (declared in manifest).
 */
@AndroidEntryPoint
class ScreenShareService : Service() {

    companion object {
        const val CHANNEL_ID = "screen_share"
        const val NOTIF_ID = 1003
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
    }

    @Inject lateinit var callManager: CallManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        intent?.getParcelableExtra<Intent>(EXTRA_DATA)?.let { projectionData ->
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            if (resultCode == Activity.RESULT_OK) {
                callManager.startScreenShare(projectionData)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        callManager.stopScreenShare()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle("Screen sharing")
            .setContentText("Your screen is being shared")
            .setOngoing(true)
            .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Screen Share",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
