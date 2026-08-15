package com.kimimobile.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.kimimobile.MainActivity
import com.kimimobile.R

/**
 * Keeps long agent runs alive when the screen goes off.
 *
 * Android aggressively suspends network work for backgrounded apps, which
 * kills multi-step agent loops mid-flight. This runs as a foreground service
 * with a partial wakelock while a run is active, and the notification carries
 * a Stop action so it's never a battery drain you can't cancel.
 */
class WakeLockService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                release()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> acquire(intent?.getStringExtra(EXTRA_LABEL) ?: "Working…")
        }
        return START_STICKY
    }

    private fun acquire(label: String) {
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification(label))

        if (wakeLock?.isHeld == true) return
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
            setReferenceCounted(false)
            // Safety net: never hold the CPU awake for more than 30 minutes,
            // even if something forgets to release.
            acquire(30 * 60 * 1000L)
        }
    }

    private fun release() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun buildNotification(label: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, WakeLockService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kimi Mobile")
            .setContentText(label)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Active runs",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shown while an agent run is in progress"
                setShowBadge(false)
            }
        )
    }

    companion object {
        private const val CHANNEL_ID = "kimi_active_runs"
        private const val NOTIFICATION_ID = 42
        private const val WAKELOCK_TAG = "KimiMobile::AgentRun"
        private const val ACTION_STOP = "com.kimimobile.STOP_RUN"
        private const val EXTRA_LABEL = "label"

        fun start(context: Context, label: String) {
            val intent = Intent(context, WakeLockService::class.java)
                .putExtra(EXTRA_LABEL, label)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, WakeLockService::class.java).setAction(ACTION_STOP)
                )
            }
        }
    }
}
