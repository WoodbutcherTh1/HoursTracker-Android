package com.hourstracker.app.data

import android.content.Context
import com.hourstracker.app.BuildConfig
import com.hourstracker.app.domain.ShiftReminderSchedule
import com.hourstracker.app.service.ServiceNotifier
import com.hourstracker.app.service.ShiftController
import com.hourstracker.data.AuditAction
import com.hourstracker.data.AuditActor
import com.hourstracker.data.AuditLog
import com.hourstracker.data.AuditedShiftRepository
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
    // Tests pass an ordinary AES key: the Android Keystore does not exist on the JVM.
    idCipher: IdCipher? = null,
) {
    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val flags = AppFlags(context)
    private val database = AppDatabase.open(context)

    /** The activity log: what changed on this phone, without any personal values. */
    val audit = AuditLog(database.auditLog(), appScope)

    private val idStore = SecureIdStore(context, idCipher)
    val settings: SettingsRepository = AuditedSettingsRepository(PrefsSettingsRepository(context, idStore), audit)

    // Seeding and cleanup use the plain repository: only what a person does belongs in the activity log.
    private val plainShifts: ShiftRepository = RoomShiftRepository(database.workSessions())
    val shifts: ShiftRepository = AuditedShiftRepository(plainShifts, audit)
    private val breakReminderManager = BreakReminderManager(context)

    private val shiftReminders = ShiftReminderManager(context)
    private val summaryNotifier = ShiftSummaryNotifier(context, shifts, settings, flags, ::deviceCalendar)

    val controller = ShiftController(shifts, settings, ::deviceCalendar, ServiceNotifier(context), breakReminderManager, summaryNotifier)

    init {
        if (seedMockShifts) seedMockShiftsOnce()
        // The owner's retention choice for shifts runs at every start. Off (0) by default.
        appScope.launch { applyShiftRetention() }
        // Keep the activity log to the length the owner of the data chose.
        appScope.launch { audit.purgeOlderThan(settings.auditRetentionDays.value) }
        // Re-arm the shift reminder whenever it, the usual start time or the rest days change (and at every app start).
        appScope.launch {
            combine(settings.settings, settings.shiftReminderEnabled, settings.shiftReminderMinutesBefore) { _, _, _ -> }
                .collect { rescheduleShiftReminder() }
        }
    }

    /** Deletes everything the app keeps on the phone ("Delete all my data"). Returns the steps that failed; empty means all done. */
    suspend fun eraseAllData(): List<String> =
        DataEraser(appContext, database, audit, idStore) {
            shiftReminders.schedule(null)
            breakReminderManager.cancelBreakEndReminder()
        }.erase()

    /** The day before which shifts count as too old for [years], or null when nothing is ever too old. */
    fun retentionCutoff(years: Int): java.time.Instant? {
        if (years <= 0) return null
        val calendar = deviceCalendar()
        return calendar.addMonths(calendar.startOfDay(calendar.now()), -12 * years)
    }

    /** How many finished shifts the retention choice [years] would delete right now. */
    suspend fun shiftsOlderThan(years: Int): Int = retentionCutoff(years)?.let { plainShifts.countDatedBefore(it) } ?: 0

    /** Deletes the shifts older than the chosen retention and records one line in the activity log. Returns how many went. */
    suspend fun applyShiftRetention(): Int {
        val years = settings.shiftRetentionYears.value
        val cutoff = retentionCutoff(years) ?: return 0
        val removed = plainShifts.deleteDatedBefore(cutoff)
        if (removed > 0) audit.log(AuditAction.RETENTION_CLEANUP, metadata = mapOf("removed" to removed, "olderThanYears" to years), actor = AuditActor.SYSTEM)
        return removed
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
            .forEach { plainShifts.delete(it.id) }
        week.shifts.forEach { plainShifts.upsert(it) }
    }

    /** Debug builds only, once: gives History something to show for design review. Release builds start empty. */
    private fun seedMockShiftsOnce() {
        appScope.launch {
            if (flags.mockSeeded || shifts.count() > 0) return@launch
            MockShifts.build(deviceCalendar()).forEach { plainShifts.upsert(it) }
            flags.mockSeeded = true
        }
    }
}
