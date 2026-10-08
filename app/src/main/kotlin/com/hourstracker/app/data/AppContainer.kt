package com.hourstracker.app.data

import android.content.Context
import com.hourstracker.app.BuildConfig
import com.hourstracker.app.domain.ShiftReminderSchedule
import com.hourstracker.app.service.ServiceNotifier
import com.hourstracker.app.service.ShiftController
import com.hourstracker.data.RoomShiftRepository
import com.hourstracker.data.ShiftRepository
import com.hourstracker.data.db.AppDatabase
import com.hourstracker.model.IosCalendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Manual dependency injection: one place that builds the long-lived objects (no Hilt). */
class AppContainer(
    context: Context,
    // Tests pass a fixed clock here so screens render the same every run; production reads the device.
    private val calendarFactory: () -> IosCalendar = { IosCalendar.device() },
    // Debug builds fill History with mock shifts; screenshot tests turn that off and bring their own.
    seedMockShifts: Boolean = BuildConfig.DEBUG,
) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val flags = AppFlags(context)
    val settings: SettingsRepository = PrefsSettingsRepository(context, SecureIdStore(context))
    private val database = AppDatabase.open(context)
    val shifts: ShiftRepository = RoomShiftRepository(database.workSessions())
    private val breakReminderManager = BreakReminderManager(context)

    private val shiftReminders = ShiftReminderManager(context)
    private val summaryNotifier = ShiftSummaryNotifier(context, shifts, settings, flags, ::deviceCalendar)

    val controller = ShiftController(shifts, settings, ::deviceCalendar, ServiceNotifier(context), breakReminderManager, summaryNotifier)

    init {
        if (seedMockShifts) seedMockShiftsOnce()
        // Re-arm the shift reminder whenever it, the usual start time or the rest days change (and at every app start).
        appScope.launch {
            combine(settings.settings, settings.shiftReminderEnabled, settings.shiftReminderMinutesBefore) { _, _, _ -> }
                .collect { rescheduleShiftReminder() }
        }
    }

    /** Arms the alarm for the next shift reminder, or clears it when the reminder is off. */
    fun rescheduleShiftReminder() {
        val calendar = deviceCalendar()
        val next = if (settings.shiftReminderEnabled.value) {
            ShiftReminderSchedule.next(calendar.now(), settings.settings.value, settings.shiftReminderMinutesBefore.value, calendar)
        } else {
            null
        }
        shiftReminders.schedule(next)
    }

    /** `Calendar.current`: time zone, first weekday and minimal days follow the device. */
    fun deviceCalendar(): IosCalendar = calendarFactory()

    /** Debug builds only: replaces last week's shifts with a known 54 hour week (see [com.hourstracker.app.domain.DebugScenarios]). */
    suspend fun loadOvertimeWeek() {
        val week = com.hourstracker.app.domain.DebugScenarios.overtimeWeek(deviceCalendar())
        shifts.shifts.first()
            .filter { it.session.date >= week.from && it.session.date < week.to }
            .forEach { shifts.delete(it.id) }
        week.shifts.forEach { shifts.upsert(it) }
    }

    /** Debug builds only, once: gives History something to show for design review. Release builds start empty. */
    private fun seedMockShiftsOnce() {
        appScope.launch {
            if (flags.mockSeeded || shifts.count() > 0) return@launch
            MockShifts.build(deviceCalendar()).forEach { shifts.upsert(it) }
            flags.mockSeeded = true
        }
    }
}
