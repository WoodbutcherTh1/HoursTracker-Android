package com.hourstracker.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.hourstracker.app.R

/**
 * Notification channels for the app.
 * Must be created before showing notifications on Android O+.
 */
object NotificationChannels {
    const val SHIFT_TIMER = "shift_timer"
    const val BREAK_REMINDERS = "break_reminders"
    const val SHIFT_SUMMARY = "shift_summary"
    const val SHIFT_REMINDERS = "shift_reminders"

    fun createAll(context: Context) {
        run {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Shift timer (ongoing notification while clocked in)
            manager.createNotificationChannel(
                NotificationChannel(
                    SHIFT_TIMER,
                    context.getString(R.string.notification_channel_shift_timer),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.notification_channel_shift_timer_description)
                    setShowBadge(false)
                }
            )

            // Break end reminders
            manager.createNotificationChannel(
                NotificationChannel(
                    BREAK_REMINDERS,
                    context.getString(R.string.notification_channel_break_reminders),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notification_channel_break_reminders_description)
                }
            )

            // Shift summary (after clock out)
            manager.createNotificationChannel(
                NotificationChannel(
                    SHIFT_SUMMARY,
                    context.getString(R.string.notification_channel_shift_summary),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.notification_channel_shift_summary_description)
                }
            )

            // Shift reminders (before shift starts)
            manager.createNotificationChannel(
                NotificationChannel(
                    SHIFT_REMINDERS,
                    context.getString(R.string.notification_channel_shift_reminders),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notification_channel_shift_reminders_description)
                }
            )
        }
    }
}
