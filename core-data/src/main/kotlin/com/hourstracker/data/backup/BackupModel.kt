package com.hourstracker.data.backup

import java.time.Instant
import java.util.UUID

/** The iOS account backup (`user_backups.payload`): the settings and every shift. */
data class BackupPayload(val settings: BackupSettings, val sessions: List<BackupSession>)

class BackupBreak(val start: Instant, val end: Instant?) {
    override fun equals(other: Any?) = other is BackupBreak && start == other.start && end == other.end

    override fun hashCode() = 31 * start.hashCode() + (end?.hashCode() ?: 0)

    override fun toString() = "BackupBreak($start, $end)"
}

/** One shift as the iOS app stores it. [date] is the start of the shift's local day in the zone it was recorded in. */
data class BackupSession(
    val id: UUID,
    val date: Instant,
    val clockIn: Instant,
    val clockOut: Instant?,
    val isManualEntry: Boolean,
    val isAIImported: Boolean,
    val breakMinutes: Int,
    val breaks: List<BackupBreak>,
    /** `regular`, `restDay`, `holiday` or `sick`. */
    val dayType: String,
    val isNightShift: Boolean,
    val notes: String?,
    val modifiedAt: Instant,
    /** `null` is the main workplace. */
    val workplaceId: UUID?,
)

data class BackupLeaveDay(val id: UUID, val date: Instant, /** `vacation` or `recuperation`. */ val kind: String)

/** A second job with its own settings (its `additionalWorkplaces` is always empty). */
data class BackupWorkplace(val id: UUID, val colorIndex: Int, val settings: BackupSettings)

/** Every key of the iOS `WorkplaceSettings`, including the ones the Android app does not use yet (location, leave days, other jobs). */
data class BackupSettings(
    val workplaceName: String,
    val contractorName: String?,
    val workerFullName: String,
    val workerIdNumber: String,
    val employeeNumber: String,
    val hourlyRate: Double,
    val dailyGasAllowance: Double,
    val standardDayHours: Double,
    val ot125HoursCap: Double,
    val locationLatitude: Double?,
    val locationLongitude: Double?,
    val locationRadiusMeters: Double,
    val maritalStatus: String,
    val hasChildren: Boolean,
    val numberOfChildren: Int,
    val spouseEmployed: Boolean,
    val birthDate: Instant?,
    val payrollStartDay: Int,
    val restDayWeekday: Int,
    val secondRestDayWeekday: Int?,
    val defaultBreakMinutes: Int,
    val breaksArePaid: Boolean,
    val nightStandardDayHours: Double,
    val weeklyStandardHours: Double,
    val weeklyOvertimeCapHours: Double,
    val currencyCode: String,
    val arrivalRemindersEnabled: Boolean,
    val expectedShiftStartHour: Int,
    val expectedShiftStartMinute: Int,
    val leaveDays: List<BackupLeaveDay>,
    val additionalWorkplaces: List<BackupWorkplace>,
    val modifiedAt: Instant,
)
