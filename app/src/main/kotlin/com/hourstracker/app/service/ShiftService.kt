package com.hourstracker.app.service

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
import com.hourstracker.app.HoursTrackerApp
import com.hourstracker.app.MainActivity
import com.hourstracker.app.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The ongoing notification of a running shift: a foreground service (type `specialUse`, declared in the manifest) so
 * that Android keeps the timer visible while the app is in the background. It stops when the shift is closed.
 *
 * The timer is the system chronometer, so it ticks without waking the app. While an unpaid break runs the paid clock is
 * stopped, so the chronometer is replaced by "On break" text. The buttons act through the same [ShiftController] as Home.
 */
class ShiftService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as HoursTrackerApp).container
        when (intent?.action) {
            ACTION_SHOW -> {
                val notification = buildNotification(this, intent)
                startForegroundCompat(notification)
            }
            ACTION_TOGGLE_BREAK -> scope.launch { container.controller.toggleBreak() }
            ACTION_CLOCK_OUT -> scope.launch { container.controller.clockOut() }
            ACTION_HIDE -> {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    companion object {
        const val ACTION_SHOW = "com.hourstracker.app.SHOW_SHIFT"
        const val ACTION_HIDE = "com.hourstracker.app.HIDE_SHIFT"
        const val ACTION_TOGGLE_BREAK = "com.hourstracker.app.TOGGLE_BREAK"
        const val ACTION_CLOCK_OUT = "com.hourstracker.app.CLOCK_OUT"
        const val EXTRA_CLOCK_IN = "clockInEpochMs"
        const val EXTRA_PAID_ELAPSED_MS = "paidElapsedMs"
        const val EXTRA_ON_BREAK = "onBreak"
        const val EXTRA_BREAK_SINCE = "breakSinceEpochMs"
        const val EXTRA_PAUSED = "paused"
        private const val CHANNEL_ID = "shift"
        private const val NOTIFICATION_ID = 1

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, context.getString(R.string.home_live_pay), NotificationManager.IMPORTANCE_LOW).apply {
                        setShowBadge(false)
                    },
                )
            }
        }

        private fun action(context: Context, action: String, label: Int, request: Int): NotificationCompat.Action {
            val intent = Intent(context, ShiftService::class.java).setAction(action)
            val pending = PendingIntent.getService(context, request, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            return NotificationCompat.Action.Builder(0, context.getString(label), pending).build()
        }

        fun buildNotification(context: Context, intent: Intent): Notification {
            ensureChannel(context)
            val onBreak = intent.getBooleanExtra(EXTRA_ON_BREAK, false)
            val paused = intent.getBooleanExtra(EXTRA_PAUSED, false)
            val clockIn = intent.getLongExtra(EXTRA_CLOCK_IN, System.currentTimeMillis())
            val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            fun timeText(epochMs: Long) = timeFormat.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .addAction(action(context, ACTION_TOGGLE_BREAK, if (onBreak) R.string.home_break_end else R.string.home_break_start, 1))
                .addAction(action(context, ACTION_CLOCK_OUT, R.string.home_clock_out, 2))

            if (onBreak) {
                val since = timeText(intent.getLongExtra(EXTRA_BREAK_SINCE, System.currentTimeMillis()))
                builder.setContentTitle(context.getString(R.string.home_break_on_break))
                    .setContentText(context.getString(R.string.home_status_break, since))
            } else {
                builder.setContentTitle(context.getString(R.string.home_status_working, timeText(clockIn)))
            }
            if (paused) {
                builder.setContentText(context.getString(R.string.home_timer_paused))
            } else {
                // The chronometer counts from `when`: the clock-in moved forward by the unpaid breaks already taken.
                val paidElapsed = intent.getLongExtra(EXTRA_PAID_ELAPSED_MS, 0)
                builder.setUsesChronometer(true).setShowWhen(true).setWhen(System.currentTimeMillis() - paidElapsed)
            }
            return builder.build()
        }
    }
}
