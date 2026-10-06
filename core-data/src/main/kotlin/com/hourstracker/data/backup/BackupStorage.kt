package com.hourstracker.data.backup

import com.hourstracker.data.db.LeaveDayEntity
import com.hourstracker.data.db.WorkplaceSettingsEntity

/** Maps the settings of a backup to database rows, and back. The ID number is not part of any row. */
object BackupStorage {
    const val MAIN_WORKPLACE_ID = "main"

    /** The main workplace first, then every extra job. */
    fun workplaceRows(settings: BackupSettings): List<WorkplaceSettingsEntity> =
        listOf(settings.toRow(MAIN_WORKPLACE_ID, 0)) + settings.additionalWorkplaces.map { it.settings.toRow(it.id.toString(), it.colorIndex) }

    fun leaveRows(settings: BackupSettings): List<LeaveDayEntity> =
        settings.leaveDays.map { LeaveDayEntity(it.id.toString(), it.date.toEpochMilli(), it.kind) }

    private fun BackupSettings.toRow(id: String, colorIndex: Int) = WorkplaceSettingsEntity(
        id = id,
        colorIndex = colorIndex,
        workplaceName = workplaceName,
        contractorName = contractorName,
        workerFullName = workerFullName,
        employeeNumber = employeeNumber,
        hourlyRate = hourlyRate,
        dailyGasAllowance = dailyGasAllowance,
        standardDayHours = standardDayHours,
        ot125HoursCap = ot125HoursCap,
        locationLatitude = locationLatitude,
        locationLongitude = locationLongitude,
        locationRadiusMeters = locationRadiusMeters,
        maritalStatus = maritalStatus,
        hasChildren = hasChildren,
        numberOfChildren = numberOfChildren,
        spouseEmployed = spouseEmployed,
        birthDate = birthDate?.toEpochMilli(),
        payrollStartDay = payrollStartDay,
        restDayWeekday = restDayWeekday,
        secondRestDayWeekday = secondRestDayWeekday,
        defaultBreakMinutes = defaultBreakMinutes,
        breaksArePaid = breaksArePaid,
        nightStandardDayHours = nightStandardDayHours,
        weeklyStandardHours = weeklyStandardHours,
        weeklyOvertimeCapHours = weeklyOvertimeCapHours,
        currencyCode = currencyCode,
        arrivalRemindersEnabled = arrivalRemindersEnabled,
        expectedShiftStartHour = expectedShiftStartHour,
        expectedShiftStartMinute = expectedShiftStartMinute,
        modifiedAt = modifiedAt.toEpochMilli(),
    )
}
