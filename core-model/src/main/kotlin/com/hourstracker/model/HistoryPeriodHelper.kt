package com.hourstracker.model

import java.time.Instant
import java.util.Locale

class PayrollPeriod(
    /** Inclusive start of the payroll window (start of day). */
    val start: Instant,
    /** Inclusive end of the payroll window (start of day for the last day). */
    val end: Instant,
    /** Month used for the header label (the month that contains `start`). */
    val labelMonth: Instant,
) {
    fun contains(date: Instant, calendar: IosCalendar): Boolean {
        val day = calendar.startOfDay(date)
        return day >= calendar.startOfDay(start) && day <= calendar.startOfDay(end)
    }

    fun days(calendar: IosCalendar): List<Instant> = HistoryPeriodHelper.days(start, end, calendar)
}

/** One day cell in a padded payroll week row. */
class PayrollWeekDay(val date: Instant, val isInPeriod: Boolean)

/** A full calendar week (always 7 days) covering part of a payroll period. */
class PayrollWeek(val days: List<PayrollWeekDay>)

object HistoryPeriodHelper {
    /** Clamp to 1...28 so every month has a valid start day. */
    fun normalizedStartDay(day: Int): Int = minOf(maxOf(day, 1), 28)

    /** Payroll cycle whose start falls in the same calendar month as [monthAnchor]. */
    fun payrollPeriodForMonthAnchor(monthAnchor: Instant, startDay: Int, calendar: IosCalendar): PayrollPeriod {
        val day = normalizedStartDay(startDay)
        val start = calendar.startOfDay(calendar.dateFrom(calendar.year(monthAnchor), calendar.month(monthAnchor), day))
        val nextStart = calendar.addMonths(start, 1)
        val end = calendar.addDays(nextStart, -1)
        return PayrollPeriod(start, calendar.startOfDay(end), start)
    }

    /** The payroll period that contains a given date. */
    fun payrollPeriodContaining(date: Instant, startDay: Int, calendar: IosCalendar): PayrollPeriod {
        val day = normalizedStartDay(startDay)
        val dateDay = calendar.dayOfMonth(date)
        var anchor = date
        if (dateDay < day) {
            anchor = calendar.addMonths(date, -1)
        }
        return payrollPeriodForMonthAnchor(anchor, day, calendar)
    }

    fun shiftPayrollAnchor(anchor: Instant, months: Int, calendar: IosCalendar): Instant = calendar.addMonths(anchor, months)

    fun days(start: Instant, end: Instant, calendar: IosCalendar): List<Instant> {
        val startDay = calendar.startOfDay(start)
        val endDay = calendar.startOfDay(end)
        if (!(startDay <= endDay)) return listOf(startDay)

        val result = ArrayList<Instant>()
        var cursor = startDay
        while (cursor <= endDay) {
            result.add(cursor)
            cursor = calendar.addDays(cursor, 1)
        }
        return result
    }

    /** Start of the calendar week that contains [date] (honors the calendar's first weekday). */
    fun startOfWeek(date: Instant, calendar: IosCalendar): Instant {
        val day = calendar.startOfDay(date)
        val weekday = calendar.weekday(day)
        val delta = (weekday - calendar.firstWeekday + 7) % 7
        return calendar.addDays(day, -delta)
    }

    /** Split a payroll period into padded week rows (always 7 columns). */
    fun weekRows(period: PayrollPeriod, calendar: IosCalendar): List<PayrollWeek> {
        val periodDays = period.days(calendar)
        val first = periodDays.firstOrNull() ?: return emptyList()
        val last = periodDays.lastOrNull() ?: return emptyList()

        val gridStart = startOfWeek(first, calendar)
        val lastWeekStart = startOfWeek(last, calendar)
        val gridEnd = calendar.addDays(lastWeekStart, 6)

        val weeks = ArrayList<PayrollWeek>()
        var cursor = gridStart
        while (cursor <= gridEnd) {
            val days = (0 until 7).map { offset ->
                val start = calendar.startOfDay(calendar.addDays(cursor, offset))
                PayrollWeekDay(start, period.contains(start, calendar))
            }
            weeks.add(PayrollWeek(days))
            cursor = calendar.addDays(cursor, 7)
        }
        return weeks
    }

    fun daysInMonth(date: Instant, calendar: IosCalendar): List<Instant> {
        val count = calendar.daysInMonthCount(date)
        val start = calendar.dateFrom(calendar.year(date), calendar.month(date), 1)
        return (1..count).map { day -> calendar.addDays(start, day - 1) }
    }

    fun shiftMonth(date: Instant, value: Int, calendar: IosCalendar): Instant = calendar.addMonths(date, value)

    fun formatHoursClock(hours: Double): String {
        val totalSeconds = roundedAwayFromZero(hours * 3600).toInt()
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        return String.format(Locale.ROOT, "%02d:%02d", h, m)
    }

    /** Per-day worked hours for the calendar week containing [date] (7 values, week-start first). */
    fun dailyHoursForWeek(date: Instant, sessions: List<WorkSession>, calendar: IosCalendar): List<Double> {
        val weekStart = calendar.startOfWeekOfYear(date)
        val completed = sessions.filter { it.clockOut != null }
        return (0 until 7).map { offset ->
            val day = calendar.addDays(weekStart, offset)
            completed
                .filter { calendar.isSameDay(it.date, day) }
                .fold(0.0) { sum, item -> sum + item.totalHours }
        }
    }

    /** Normalize daily hours to 0...1 chart heights. */
    fun normalizedDayHeights(dailyHours: List<Double>, minimumNonZero: Double = 0.12): List<Double> {
        val hours = if (dailyHours.isEmpty()) List(7) { 0.0 } else dailyHours
        val peak = hours.maxOrNull() ?: 0.0
        if (!(peak > 0.01)) return List(hours.size) { 0.0 }
        return hours.map { value ->
            if (!(value > 0.01)) 0.0 else swiftMin(1.0, swiftMax(minimumNonZero, value / peak))
        }
    }
}
