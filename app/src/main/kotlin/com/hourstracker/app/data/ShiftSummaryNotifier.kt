package com.hourstracker.app.data

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.hourstracker.app.MainActivity
import com.hourstracker.app.R
import com.hourstracker.data.ShiftRecord
import com.hourstracker.data.ShiftRepository
import com.hourstracker.model.HistoryPeriodHelper
import com.hourstracker.model.IosCalendar
import com.hourstracker.model.OvertimeCalculator
import com.hourstracker.model.PayFormatter
import kotlinx.coroutines.flow.first

/** The "shift complete" notification with hours and pay, for a shift closed from the notification (Home shows its own sheet). */
class ShiftSummaryNotifier(
    private val context: Context,
    private val shifts: ShiftRepository,
    private val settings: SettingsRepository,
    private val flags: AppFlags,
    private val calendar: () -> IosCalendar,
) {
    suspend fun notifyClosed(closed: ShiftRecord) {
        if (!settings.shiftSummaryEnabled.value) return
        val config = settings.settings.value
        val sessions = shifts.shifts.first().map { it.session }.filter { it.clockOut != null || it.id == closed.id }
        val hours = HistoryPeriodHelper.formatHoursClock(closed.session.effectiveHours)
        val text = if (config.hourlyRate > 0) {
            val breakdown = OvertimeCalculator.breakdown(closed.session, sessions, config, calendar())
            val amount = if (flags.showNet.value) breakdown.netPay else breakdown.totalPay
            val locale = context.resources.configuration.locales[0]
            context.getString(R.string.shift_summary_text, hours, PayFormatter.string(amount, breakdown.currencyCode, locale))
        } else {
            context.getString(R.string.shift_summary_text_hours, hours)
        }
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        // Hours and pay are private: on a locked screen only the title shows (the lock-screen setting decides which version is used).
        val lockScreenVersion = NotificationCompat.Builder(context, NotificationChannels.SHIFT_SUMMARY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.shift_summary_title))
            .build()
        val notification = NotificationCompat.Builder(context, NotificationChannels.SHIFT_SUMMARY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.shift_summary_title))
            .setContentText(text)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(lockScreenVersion)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val NOTIFICATION_ID = 3002
    }
}
