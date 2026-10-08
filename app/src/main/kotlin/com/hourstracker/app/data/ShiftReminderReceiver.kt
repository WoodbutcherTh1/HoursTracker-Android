package com.hourstracker.app.data

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.hourstracker.app.HoursTrackerApp
import com.hourstracker.app.MainActivity
import com.hourstracker.app.R
import com.hourstracker.app.domain.ShiftClock
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Shows "your shift starts soon" unless the worker is already clocked in, then arms the next reminder. */
class ShiftReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as HoursTrackerApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(context, container)
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 3001

        /** Shows the reminder unless the worker is already clocked in or has turned it off, then arms the next one. */
        suspend fun handle(context: Context, container: AppContainer) {
            try {
                val alreadyWorking = ShiftClock.active(container.shifts.shifts.first()) != null
                if (container.settings.shiftReminderEnabled.value && !alreadyWorking) show(context, container)
            } finally {
                container.rescheduleShiftReminder()
            }
        }

        private fun show(context: Context, container: AppContainer) {
            val settings = container.settings.settings.value
            val time = java.time.LocalTime.of(settings.expectedShiftStartHour, settings.expectedShiftStartMinute)
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(context, NotificationChannels.SHIFT_REMINDERS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.shift_reminder_title))
                .setContentText(context.getString(R.string.shift_reminder_text, DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(time)))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification)
        }
    }
}
