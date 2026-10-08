package com.hourstracker.app.service

import com.hourstracker.app.data.BreakReminderManager
import com.hourstracker.app.data.SettingsRepository
import com.hourstracker.app.domain.ShiftClock
import com.hourstracker.data.ShiftRecord
import com.hourstracker.data.ShiftRepository
import com.hourstracker.model.IosCalendar
import kotlinx.coroutines.flow.first

/** What the running-shift notification needs to know. */
interface ShiftNotifier {
    /** Shows or refreshes the ongoing notification for [record]. */
    fun show(record: ShiftRecord, breaksArePaid: Boolean)

    fun hide()
}

/**
 * Clock in, break and clock out, shared by the Home screen and the notification buttons. Each action reads the
 * stored state, applies the rule from [ShiftClock], saves, and refreshes the notification.
 */
class ShiftController(
    private val shifts: ShiftRepository,
    private val settings: SettingsRepository,
    private val calendar: () -> IosCalendar,
    private val notifier: ShiftNotifier,
    private val breakReminder: BreakReminderManager? = null,
) {
    suspend fun clockIn(): ShiftRecord? {
        val cal = calendar()
        val record = ShiftClock.clockIn(shifts.shifts.first(), settings.settings.value, cal, cal.now()) ?: return null
        shifts.upsert(record)
        notifier.show(record, settings.settings.value.breaksArePaid)
        return record
    }

    /** Starts a break, or ends the running one. Returns the updated shift, or null when there is no running shift. */
    suspend fun toggleBreak(): ShiftRecord? {
        val active = ShiftClock.active(shifts.shifts.first()) ?: return null
        val now = calendar().now()
        val config = settings.settings.value
        val wasOnBreak = active.session.isOnBreak
        val updated = if (wasOnBreak) ShiftClock.endBreak(active, config, now) else ShiftClock.startBreak(active, now)
        if (updated == null) return null
        shifts.upsert(updated)
        notifier.show(updated, config.breaksArePaid)

        // Handle break reminders
        breakReminder?.let { manager ->
            if (wasOnBreak) {
                // Resumed from break - cancel reminder
                manager.cancelBreakEndReminder()
            } else if (settings.breakRemindersEnabled.value) {
                // Started break - schedule reminder
                manager.scheduleBreakEndReminder(
                    updated,
                    settings.breakReminderMinutesBefore.value,
                    config.defaultBreakMinutes
                )
            }
        }

        return updated
    }

    /** Closes the running shift and returns it, or null when nothing was running. */
    suspend fun clockOut(): ShiftRecord? {
        val active = ShiftClock.active(shifts.shifts.first()) ?: return null
        val cal = calendar()
        val closed = ShiftClock.clockOut(active, settings.settings.value, cal, cal.now())
        shifts.upsert(closed)
        notifier.hide()
        breakReminder?.cancelBreakEndReminder() // Cancel any pending break reminder
        return closed
    }

    /** Re-shows the notification for a shift that is still running (after a restart of the app or the phone). */
    suspend fun restoreNotification() {
        val active = ShiftClock.active(shifts.shifts.first())
        if (active == null) notifier.hide() else notifier.show(active, settings.settings.value.breaksArePaid)
    }
}
