package com.mitesh.livetranslator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Foreground service that keeps the microphone open while the screen is off
 * or another app is in front. Shows the latest translation in its notification.
 */
class ListenService : Service(), Engine.Listener {

    private var latest: Line? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Engine.stop(this)
                return START_NOT_STICKY
            }
            else -> {
                ensureChannel()
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, build(),
                    if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
                )
                Engine.addListener(this)
                Engine.start(this)
            }
        }
        return START_NOT_STICKY
    }

    override fun onState(state: Engine.State) {
        if (state == Engine.State.IDLE) {
            Engine.removeListener(this)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else notifyUpdate()
    }

    override fun onFinal(line: Line) {
        latest = line
        notifyUpdate()
    }

    override fun onDestroy() {
        Engine.removeListener(this)
        super.onDestroy()
    }

    private fun notifyUpdate() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build())
    }

    private fun build(): Notification {
        val immutable = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            immutable
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ListenService::class.java).setAction(ACTION_STOP), immutable
        )
        val title = when (Engine.state) {
            Engine.State.RECONNECTING -> "Bayan is reconnecting"
            Engine.State.CONNECTING -> "Bayan is connecting"
            else -> "Bayan is listening"
        }
        val text = latest?.target?.takeIf { it.isNotBlank() } ?: "Arabic and English, translated live"
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setColor(ContextCompat.getColor(this, R.color.sand))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Listening", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while Bayan is listening, with the latest translation"
                    setShowBadge(false)
                }
            )
        }
    }

    companion object {
        private const val CHANNEL = "listening"
        private const val NOTIFICATION_ID = 7
        private const val ACTION_STOP = "com.mitesh.livetranslator.STOP"

        fun start(c: Context) =
            ContextCompat.startForegroundService(c, Intent(c, ListenService::class.java))
    }
}
