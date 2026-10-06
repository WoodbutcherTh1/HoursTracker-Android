package com.hourstracker.app.domain

import com.hourstracker.data.ShiftRecord
import com.hourstracker.model.DayType
import com.hourstracker.model.IosCalendar
import com.hourstracker.model.OvertimeCalculator
import com.hourstracker.model.WorkSession
import com.hourstracker.model.WorkplaceSettings
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** What the manual-entry form holds. Times are wall-clock minutes since midnight (hour * 60 + minute). */
data class ManualEntryInput(
    val date: LocalDate,
    val dayType: DayType,
    val useDirectHours: Boolean,
    val directHours: Double,
    val clockInMinutes: Int,
    val clockOutMinutes: Int,
    val breakMinutes: Int,
    val isNightShift: Boolean,
    val notes: String,
)

/** The manual-entry rules of the iOS app: validation, how a shift is built, and sick-day previews. */
object ManualEntry {
    enum class Problem { ZeroDuration, BreakExceedsShift }

    /** Sick days per calendar year (they accrue at about 1.5 a month). */
    const val SICK_DAYS_PER_YEAR_CAP = 18

    /** Shift length in minutes as entered, before any break deduction; null for identical clock-in and clock-out times. */
    fun enteredShiftMinutes(input: ManualEntryInput): Int? {
        if (input.dayType == DayType.Holiday || input.dayType == DayType.Sick) return null
        if (input.useDirectHours) return (input.directHours * 60).toInt()
        if (input.clockInMinutes == input.clockOutMinutes) return null
        var minutes = input.clockOutMinutes - input.clockInMinutes
        if (minutes <= 0) minutes += 24 * 60 // an overnight shift
        return minutes
    }

    /** The reason the form cannot be saved, or null. Holiday and sick days have nothing to validate. */
    fun validate(input: ManualEntryInput): Problem? {
        if (input.dayType == DayType.Holiday || input.dayType == DayType.Sick) return null
        val shift = enteredShiftMinutes(input)
        if (shift == null || shift <= 0) return Problem.ZeroDuration
        if (input.breakMinutes < 0 || input.breakMinutes >= shift) return Problem.BreakExceedsShift
        return null
    }

    /** The day type a new entry starts with: the weekly rest day is tagged automatically. */
    fun automaticDayType(date: LocalDate, settings: WorkplaceSettings, existing: List<WorkSession>, calendar: IosCalendar): DayType {
        val hasHolidayMarked = existing.any { it.dayType == DayType.Holiday && calendar.localDate(it.date) == date }
        if (hasHolidayMarked) return DayType.Holiday
        val foundationWeekday = date.dayOfWeek.value % 7 + 1
        return if (settings.isRestDayWeekday(foundationWeekday)) DayType.RestDay else DayType.Regular
    }

    /** What marking [date] as sick would pay: the day number in its streak and the share of pay. */
    fun sickStreakPreview(date: LocalDate, existing: List<WorkSession>, calendar: IosCalendar): Pair<Int, Double> {
        val day = date.atStartOfDay(calendar.zone).toInstant()
        val sickDates = existing.filter { it.dayType == DayType.Sick }.map { calendar.startOfDay(it.date) }.toMutableSet()
        sickDates.add(day)
        val number = OvertimeCalculator.sickStreakDayNumber(day, sickDates, calendar)
        return Pair(number, OvertimeCalculator.sickPayPercentage(number))
    }

    fun sickDaysUsedInYear(date: LocalDate, existing: List<WorkSession>, calendar: IosCalendar): Int =
        existing.count { it.dayType == DayType.Sick && calendar.localDate(it.date).year == date.year }

    /** True when a new sick day on [date] would pass the yearly allowance (a day already marked sick can be re-saved). */
    fun sickCapReached(date: LocalDate, existing: List<WorkSession>, calendar: IosCalendar): Boolean {
        val alreadySick = existing.any { it.dayType == DayType.Sick && calendar.localDate(it.date) == date }
        return !alreadySick && sickDaysUsedInYear(date, existing, calendar) >= SICK_DAYS_PER_YEAR_CAP
    }

    /** The stored shift for a valid [input]. [now] stamps `modifiedAt`. */
    fun build(input: ManualEntryInput, settings: WorkplaceSettings, calendar: IosCalendar, now: Instant, id: UUID = UUID.randomUUID()): ShiftRecord {
        val day = input.date.atStartOfDay(calendar.zone).toInstant()
        val notes = input.notes.ifEmpty { null }

        if (input.dayType == DayType.Sick) {
            // A sick day has no worked hours: clock-in equals clock-out, and pay comes from the sick streak.
            val session = WorkSession(id = id, date = day, clockIn = day, clockOut = day, dayType = DayType.Sick)
            return ShiftRecord(session, notes = notes, isManualEntry = true, modifiedAt = now)
        }

        val clockIn: Instant
        val clockOut: Instant
        if (input.dayType == DayType.Holiday) {
            // Holiday hours are derived from the typical shift: nobody clocked a holiday.
            val expected = settings.expectedShift(day, calendar)
            clockIn = expected.clockIn
            clockOut = expected.clockOut
        } else if (input.useDirectHours) {
            clockIn = Instant.ofEpochSecond(day.epochSecond + 8 * 3600)
            clockOut = Instant.ofEpochSecond(clockIn.epochSecond + Math.round(input.directHours * 3600))
        } else {
            val wallIn = calendar.setTime(day, input.clockInMinutes / 60, input.clockInMinutes % 60, 0)
            val wallOut = calendar.setTime(day, input.clockOutMinutes / 60, input.clockOutMinutes % 60, 0)
            val resolved = WorkSession.resolveClockPair(wallIn, wallOut, calendar)
            clockIn = resolved.clockIn
            clockOut = resolved.clockOut
        }
        val session = WorkSession(
            id = id,
            date = day,
            clockIn = clockIn,
            clockOut = clockOut,
            breakMinutes = input.breakMinutes,
            dayType = input.dayType,
            isNightShift = input.isNightShift,
        )
        return ShiftRecord(session, notes = notes, isManualEntry = true, modifiedAt = now)
    }
}
