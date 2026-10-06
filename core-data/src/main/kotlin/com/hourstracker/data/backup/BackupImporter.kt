package com.hourstracker.data.backup

import com.hourstracker.data.ShiftRecord
import com.hourstracker.model.BreakInterval
import com.hourstracker.model.DayType
import com.hourstracker.model.WorkSession
import com.hourstracker.model.WorkplaceSettings
import com.hourstracker.model.MaritalStatus
import java.time.Instant
import java.time.ZoneId

/**
 * How a backup's days are placed on this device.
 *
 * iOS stores a shift's `date` as the start of its local day in the zone it was recorded in, written in UTC:
 * a shift on 15 July recorded in Israel has `date = 2026-07-14T21:00:00Z`. Read that in a zone west of Israel and
 * it lands on 14 July, a whole day early. So the day is read in [sourceZone] (Israel, where the iOS app is used)
 * and then placed at midnight of the same calendar day in [deviceZone]. When `date` is not a midnight in
 * [sourceZone] the backup came from another zone, and it is read in [deviceZone] instead.
 */
class ImportOptions(
    val deviceZone: ZoneId,
    val sourceZone: ZoneId = ZoneId.of("Asia/Jerusalem"),
)

/** What was imported, and what the backup holds that this version of the app has nowhere to keep yet. */
class ImportResult(
    val shifts: List<ShiftRecord>,
    val settings: WorkplaceSettings,
    val settingsBackup: BackupSettings,
    /** Leave days and other jobs are read but not stored yet. */
    val unsupportedLeaveDays: Int,
    val unsupportedWorkplaces: Int,
)

object BackupImporter {
    fun import(payload: BackupPayload, options: ImportOptions): ImportResult {
        val s = payload.settings
        return ImportResult(
            shifts = payload.sessions.map { toRecord(it, options) },
            settings = toSettings(s, options),
            settingsBackup = s,
            unsupportedLeaveDays = s.leaveDays.size,
            unsupportedWorkplaces = s.additionalWorkplaces.size,
        )
    }

    fun normalizeDate(date: Instant, options: ImportOptions): Instant {
        val inSource = date.atZone(options.sourceZone)
        val zone = if (inSource.toLocalTime() == java.time.LocalTime.MIDNIGHT) options.sourceZone else options.deviceZone
        return date.atZone(zone).toLocalDate().atStartOfDay(options.deviceZone).toInstant()
    }

    private fun toRecord(s: BackupSession, options: ImportOptions) = ShiftRecord(
        session = WorkSession(
            id = s.id,
            date = normalizeDate(s.date, options),
            clockIn = s.clockIn,
            clockOut = s.clockOut,
            breakMinutes = s.breakMinutes,
            breaks = s.breaks.map { BreakInterval(it.start, it.end) },
            dayType = DayType.fromRaw(s.dayType) ?: DayType.Regular,
            isNightShift = s.isNightShift,
        ),
        notes = s.notes,
        isManualEntry = s.isManualEntry,
        isAIImported = s.isAIImported,
        modifiedAt = s.modifiedAt,
        workplaceId = s.workplaceId,
    )

    private fun toSettings(s: BackupSettings, options: ImportOptions) = WorkplaceSettings(
        hourlyRate = s.hourlyRate,
        dailyGasAllowance = s.dailyGasAllowance,
        standardDayHours = s.standardDayHours,
        ot125HoursCap = s.ot125HoursCap,
        locationRadiusMeters = s.locationRadiusMeters,
        maritalStatus = MaritalStatus.fromRaw(s.maritalStatus) ?: MaritalStatus.Single,
        hasChildren = s.hasChildren,
        numberOfChildren = s.numberOfChildren,
        spouseEmployed = s.spouseEmployed,
        birthDate = s.birthDate?.let { normalizeDate(it, options) },
        payrollStartDay = s.payrollStartDay,
        restDayWeekday = s.restDayWeekday,
        secondRestDayWeekday = s.secondRestDayWeekday,
        defaultBreakMinutes = s.defaultBreakMinutes,
        breaksArePaid = s.breaksArePaid,
        nightStandardDayHours = s.nightStandardDayHours,
        weeklyStandardHours = s.weeklyStandardHours,
        weeklyOvertimeCapHours = s.weeklyOvertimeCapHours,
        currencyCode = s.currencyCode,
        expectedShiftStartHour = s.expectedShiftStartHour,
        expectedShiftStartMinute = s.expectedShiftStartMinute,
    )

    /** A stored shift in the iOS backup shape. */
    fun toBackupSession(record: ShiftRecord) = BackupSession(
        id = record.id,
        date = record.session.date,
        clockIn = record.session.clockIn,
        clockOut = record.session.clockOut,
        isManualEntry = record.isManualEntry,
        isAIImported = record.isAIImported,
        breakMinutes = record.session.breakMinutes,
        breaks = record.session.breaks.map { BackupBreak(it.start, it.end) },
        dayType = record.session.dayType.raw,
        isNightShift = record.session.isNightShift,
        notes = record.notes,
        modifiedAt = record.modifiedAt,
        workplaceId = record.workplaceId,
    )
}
