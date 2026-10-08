package com.hourstracker.app.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.hourstracker.data.ShiftRecord
import java.time.Instant

/**
 * Manages break end reminder notifications.
 * Schedules a notification N minutes before a break ends.
 */
class BreakReminderManager(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedule a reminder for when the break is about to end.
     * @param shift The current shift on break
     * @param minutesBefore How many minutes before break end to remind
     * @param breakDurationMinutes The expected break duration in minutes
     */
    fun scheduleBreakEndReminder(shift: ShiftRecord, minutesBefore: Int, breakDurationMinutes: Int) {
        val activeBreak = shift.session.activeBreak ?: return
        if (breakDurationMinutes <= 0) return

        val breakEndTime = activeBreak.start.plusSeconds((breakDurationMinutes * 60).toLong())
        val reminderTime = breakEndTime.minusSeconds((minutesBefore * 60).toLong())

        // Only schedule if reminder time is in the future
        if (reminderTime.isAfter(Instant.now())) {
            val intent = Intent(context, BreakReminderReceiver::class.java).apply {
                putExtra("SHIFT_ID", shift.id)
                putExtra("BREAK_END_TIME", breakEndTime.toEpochMilli())
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                BREAK_REMINDER_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminderTime.toEpochMilli(),
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminderTime.toEpochMilli(),
                    pendingIntent
                )
            }
        }
    }

    /**
     * Cancel any scheduled break end reminder.
     */
    fun cancelBreakEndReminder() {
        val intent = Intent(context, BreakReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            BREAK_REMINDER_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    companion object {
        private const val BREAK_REMINDER_REQUEST_CODE = 1001
    }
}
