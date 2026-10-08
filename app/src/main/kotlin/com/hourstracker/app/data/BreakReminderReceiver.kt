package com.hourstracker.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.hourstracker.app.R
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Receives break end reminder alarms and shows a notification.
 */
class BreakReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val breakEndTime = intent.getLongExtra("BREAK_END_TIME", 0L)
        if (breakEndTime == 0L) return

        val now = Instant.now()
        val endInstant = Instant.ofEpochMilli(breakEndTime)
        val minutesRemaining = ((endInstant.epochSecond - now.epochSecond) / 60.0).roundToInt()

        if (minutesRemaining <= 0) return // Break already ended

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for break reminders
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_break_reminders),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_break_reminders_description)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.break_reminder_title))
            .setContentText(context.resources.getQuantityString(R.plurals.break_reminder_message, minutesRemaining, minutesRemaining))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "break_reminders"
        private const val NOTIFICATION_ID = 2001
    }
}
