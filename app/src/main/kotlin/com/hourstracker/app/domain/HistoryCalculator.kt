package com.hourstracker.app.domain

import com.hourstracker.app.data.ShiftRecord
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

    fun build(records: List<ShiftRecord>, settings: WorkplaceSettings, calendar: IosCalendar, monthOffset: Int): HistoryState {
        val period = periodFor(settings, calendar, monthOffset)
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
        val totals = OvertimeCalculator.aggregate(inPeriod, settings, calendar)
        return HistoryState(period, rows, totals.totalPay, totals.netPay, totals.totalHours, settings.currencyCode)
    }
}
