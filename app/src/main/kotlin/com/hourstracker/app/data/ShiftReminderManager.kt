package com.hourstracker.app.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.Instant

/**
 * Keeps one alarm for the next "your shift starts soon" reminder. The alarm is inexact on purpose (no special
 * permission), and each time it fires [ShiftReminderReceiver] schedules the next one.
 */
class ShiftReminderManager(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Replaces the alarm with one at [trigger], or removes it when [trigger] is null. */
    fun schedule(trigger: Instant?) {
        alarmManager.cancel(pendingIntent())
        if (trigger != null) alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.toEpochMilli(), pendingIntent())
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ShiftReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val REQUEST_CODE = 3001
    }
}
