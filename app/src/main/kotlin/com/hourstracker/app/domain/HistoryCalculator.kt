package com.hourstracker.app.domain

import com.hourstracker.data.ShiftRecord
import com.hourstracker.model.DayType
import com.hourstracker.model.HistoryPeriodHelper
import com.hourstracker.model.IosCalendar
import com.hourstracker.model.OvertimeCalculator
import com.hourstracker.model.PayrollPeriod
import com.hourstracker.model.WorkplaceSettings
import java.time.Instant
import java.util.UUID

class HistoryRow(
    val id: UUID,
    val day: Instant,
    val clockIn: Instant,
    val clockOut: Instant?,
    val hours: Double,
    val gross: Double,
    val net: Double,
    val dayType: DayType,
)

/** Which stretch of time History lists. [Payroll] is the default and matches the iOS table. */
enum class HistoryFilter { Week, Month, Payroll, Year, All }

class HistoryState(
    val period: PayrollPeriod,
    val rows: List<HistoryRow>,
    val totalGross: Double,
    val totalNet: Double,
    val totalHours: Double,
    val currencyCode: String,
)

/** Rows and totals of one payroll period. Row amounts are per shift; totals come from `aggregate`, so weekly overtime is included. */
object HistoryCalculator {
    /** [monthOffset] is 0 for the period containing now, -1 for the one before, and so on. */
    fun periodFor(settings: WorkplaceSettings, calendar: IosCalendar, monthOffset: Int): PayrollPeriod {
        val current = HistoryPeriodHelper.payrollPeriodContaining(calendar.now(), settings.payrollStartDay, calendar)
        val anchor = HistoryPeriodHelper.shiftPayrollAnchor(current.labelMonth, monthOffset, calendar)
        return HistoryPeriodHelper.payrollPeriodForMonthAnchor(anchor, settings.payrollStartDay, calendar)
    }

    /**
     * The window for [filter], moved by [offset] of its own unit (weeks, months, payroll periods or years; 0 is the one
     * containing now). [HistoryFilter.All] has no window to move: it spans every date.
     */
    fun windowFor(filter: HistoryFilter, settings: WorkplaceSettings, calendar: IosCalendar, offset: Int): PayrollPeriod {
        val now = calendar.now()
        return when (filter) {
            HistoryFilter.Payroll -> periodFor(settings, calendar, offset)
            HistoryFilter.Week -> {
                val start = calendar.addDays(calendar.startOfWeekOfYear(now), 7 * offset)
                PayrollPeriod(start, calendar.addDays(start, 6), start)
            }
            HistoryFilter.Month -> {
                val shifted = calendar.addMonths(now, offset)
                val start = calendar.startOfDay(calendar.dateFrom(calendar.year(shifted), calendar.month(shifted), 1))
                PayrollPeriod(start, calendar.addDays(calendar.addMonths(start, 1), -1), start)
            }
            HistoryFilter.Year -> {
                val year = calendar.year(now) + offset
                val start = calendar.startOfDay(calendar.dateFrom(year, 1, 1))
                PayrollPeriod(start, calendar.startOfDay(calendar.dateFrom(year, 12, 31)), start)
            }
            HistoryFilter.All -> PayrollPeriod(Instant.EPOCH, Instant.parse("2200-01-01T00:00:00Z"), calendar.startOfDay(now))
        }
    }

    fun build(
        records: List<ShiftRecord>,
        settings: WorkplaceSettings,
        calendar: IosCalendar,
        monthOffset: Int,
        filter: HistoryFilter = HistoryFilter.Payroll,
        query: String = "",
        locale: java.util.Locale = java.util.Locale.getDefault(),
    ): HistoryState {
        val period = windowFor(filter, settings, calendar, monthOffset)
        val all = records.map { it.session }
        val inPeriod = records
            .map { it.session }
            .filter { it.clockOut != null && period.contains(it.date, calendar) }
            .sortedByDescending { it.clockIn }
        val rows = inPeriod.map { session ->
            val breakdown = OvertimeCalculator.breakdown(session, all.filter { it.clockOut != null }, settings, calendar)
            HistoryRow(
                id = session.id,
                day = session.date,
                clockIn = session.clockIn,
                clockOut = session.clockOut,
                hours = session.effectiveHours,
                gross = breakdown.grossPay,
                net = breakdown.netPay,
                dayType = session.dayType,
            )
        }
        // A search narrows the rows and the totals with them.
        val shown = rows.filter { HistorySearch.matches(it, query, calendar.zone, locale) }
        val shownIds = shown.map { it.id }.toSet()
        val totals = OvertimeCalculator.aggregate(inPeriod.filter { it.id in shownIds }, settings, calendar)
        return HistoryState(period, shown, totals.totalPay, totals.netPay, totals.totalHours, settings.currencyCode)
    }
}
