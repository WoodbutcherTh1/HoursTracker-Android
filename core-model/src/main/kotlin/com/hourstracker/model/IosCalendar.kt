package com.hourstracker.model

import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.WeekFields

/**
 * The slice of Foundation's `Calendar` (Gregorian) that the pay code relies on, with the same
 * three device-dependent settings: time zone, first weekday and minimal days in the first week.
 * It also carries the clock, because iOS reads "now" from `Date()` next to `Calendar.current`.
 *
 * [firstWeekday] uses Foundation numbering: 1 = Sunday ... 7 = Saturday.
 */
class IosCalendar(
    val zone: ZoneId,
    val firstWeekday: Int,
    val minimumDaysInFirstWeek: Int,
    private val clock: Clock = Clock.systemUTC(),
) {
    init {
        require(firstWeekday in 1..7) { "firstWeekday must be 1...7" }
        require(minimumDaysInFirstWeek in 1..7) { "minimumDaysInFirstWeek must be 1...7" }
    }

    fun now(): Instant = clock.instant()

    fun localDate(instant: Instant): LocalDate = instant.atZone(zone).toLocalDate()

    fun startOfDay(instant: Instant): Instant = localDate(instant).atStartOfDay(zone).toInstant()

    /** Foundation weekday: 1 = Sunday ... 7 = Saturday. */
    fun weekday(instant: Instant): Int = (localDate(instant).dayOfWeek.value % 7) + 1

    fun isSameDay(a: Instant, b: Instant): Boolean = localDate(a) == localDate(b)

    /**
     * A wall-clock time to an instant, the way Foundation resolves it on the Mac: a time that
     * occurs twice resolves to its first occurrence, and a time that does not exist (inside a
     * spring-forward gap) resolves to the first valid instant after the gap ("next time").
     * `ZonedDateTime` would instead push it forward by the length of the gap.
     */
    private fun resolve(local: LocalDateTime): Instant {
        val transition = zone.rules.getTransition(local)
        return if (transition != null && transition.isGap) transition.instant else local.atZone(zone).toInstant()
    }

    private fun wallClock(instant: Instant): LocalDateTime = instant.atZone(zone).toLocalDateTime()

    /** `date(byAdding: .day, ...)`: the same wall-clock time, `days` calendar days later. */
    fun addDays(instant: Instant, days: Int): Instant = resolve(wallClock(instant).plusDays(days.toLong()))

    /** `date(byAdding: .month, ...)`: the same wall-clock time, clamped to the end of a shorter month. */
    fun addMonths(instant: Instant, months: Int): Instant = resolve(wallClock(instant).plusMonths(months.toLong()))

    /** `date(bySettingHour:minute:second:of:)`: that wall-clock time on the local day of [instant]. */
    fun setTime(instant: Instant, hour: Int, minute: Int, second: Int): Instant =
        resolve(localDate(instant).atTime(hour, minute, second))

    /** `date(from: DateComponents(year:month:day:))` at midnight. */
    fun dateFrom(year: Int, month: Int, day: Int): Instant = LocalDate.of(year, month, day).atStartOfDay(zone).toInstant()

    fun year(instant: Instant): Int = localDate(instant).year

    fun month(instant: Instant): Int = localDate(instant).monthValue

    fun dayOfMonth(instant: Instant): Int = localDate(instant).dayOfMonth

    /** `range(of: .day, in: .month, for:)` upper bound: the number of days in the month. */
    fun daysInMonthCount(instant: Instant): Int = YearMonth.from(localDate(instant)).lengthOfMonth()

    private fun weekFields(): WeekFields =
        WeekFields.of(DayOfWeek.of(if (firstWeekday == 1) 7 else firstWeekday - 1), minimumDaysInFirstWeek)

    /** `dateComponents([.yearForWeekOfYear, .weekOfYear], from:)`. */
    fun yearAndWeekOfYear(instant: Instant): Pair<Int, Int> {
        val date = localDate(instant)
        val fields = weekFields()
        return Pair(date.get(fields.weekBasedYear()), date.get(fields.weekOfWeekBasedYear()))
    }

    /** Start of the week (`dateInterval(of: .weekOfYear, for:).start`). */
    fun startOfWeekOfYear(instant: Instant): Instant {
        val day = localDate(instant)
        val delta = (day.dayOfWeek.value % 7 + 1 - firstWeekday + 7) % 7
        return day.minusDays(delta.toLong()).atStartOfDay(zone).toInstant()
    }

    /** `dateComponents([.year], from:to:).year`: whole years from [from] up to [to]. */
    fun wholeYears(from: Instant, to: Instant): Int {
        val start = from.atZone(zone)
        var years = to.atZone(zone).year - start.year
        while (years > 0 && start.plusYears(years.toLong()).toInstant() > to) years--
        while (start.plusYears((years + 1).toLong()).toInstant() <= to) years++
        return years
    }

    companion object {
        /** The device calendar: `Calendar.current`. */
        fun device(clock: Clock = Clock.systemDefaultZone()): IosCalendar {
            val zone = clock.zone
            val week = WeekFields.of(java.util.Locale.getDefault())
            val first = week.firstDayOfWeek.value % 7 + 1
            return IosCalendar(zone, first, week.minimalDaysInFirstWeek, clock)
        }
    }
}
