package com.hourstracker.model.golden

import com.hourstracker.model.HistoryPeriodHelper
import com.hourstracker.model.PayrollPeriod
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.time.Instant

/** Group I (periods): payroll periods, week rows, calendar helpers and the weekly hours chart. */
class PeriodGoldenTest {
    private fun periodText(c: Check, period: PayrollPeriod): Triple<String, String, String> =
        Triple(c.localDay(period.start), c.localDay(period.end), c.localDay(period.labelMonth))

    @TestFactory
    fun `payroll periods`(): List<DynamicTest> = goldenTests("period_payroll") { c ->
        val anchor = c.noon(c.case.str("anchor"))
        val startDay = c.case.optInt("startDay")!!
        val period = when (val mode = c.case.str("mode")) {
            "forMonthAnchor" -> HistoryPeriodHelper.payrollPeriodForMonthAnchor(anchor, startDay, c.cal)
            "containing" -> HistoryPeriodHelper.payrollPeriodContaining(anchor, startDay, c.cal)
            else -> error("unknown mode $mode")
        }
        val out = c.case["output"]!!.jsonObject
        c.same("period", Triple(out.str("start"), out.str("end"), out.str("labelMonth")), periodText(c, period))
    }

    @TestFactory
    fun `padded week rows`(): List<DynamicTest> = goldenTests("period_weeks") { c ->
        val anchor = c.noon(c.case.str("anchor"))
        val period = HistoryPeriodHelper.payrollPeriodForMonthAnchor(anchor, c.case.optInt("startDay")!!, c.cal)
        val actual = HistoryPeriodHelper.weekRows(period, c.cal).map { week ->
            week.days.map { Pair(c.localDay(it.date), it.isInPeriod) }
        }
        val expected = c.case["weeks"]!!.jsonArray.map { week ->
            week.jsonArray.map { Pair(it.jsonObject.str("day"), it.jsonObject.bool("inPeriod")) }
        }
        c.same("weeks", expected, actual)
    }

    @TestFactory
    fun `calendar helpers`(): List<DynamicTest> = goldenTests("period_misc") { c ->
        val args = c.case["args"]!!.jsonArray.map { it.jsonPrimitive.content }
        val expected = c.case["result"]!!.jsonArray.map { it.jsonPrimitive.content }
        val cal = c.cal
        fun day(index: Int): Instant = c.noon(args[index])
        val actual: List<String> = when (val op = c.case.str("op")) {
            "normalizedStartDay" -> listOf(HistoryPeriodHelper.normalizedStartDay(args[0].toInt()).toString())
            "days" -> HistoryPeriodHelper.days(day(0), day(1), cal).map { c.localDay(it) }
            "startOfWeek" -> listOf(c.localDay(HistoryPeriodHelper.startOfWeek(day(0), cal)))
            "daysInMonth" -> HistoryPeriodHelper.daysInMonth(day(0), cal).map { c.localDay(it) }
            "shiftMonth" -> listOf(c.localDay(HistoryPeriodHelper.shiftMonth(day(0), args[1].toInt(), cal)))
            "shiftPayrollAnchor" -> listOf(c.localDay(HistoryPeriodHelper.shiftPayrollAnchor(day(0), args[1].toInt(), cal)))
            "formatHoursClock" -> listOf(HistoryPeriodHelper.formatHoursClock(args[0].toDouble()))
            else -> error("unknown op $op")
        }
        c.same("result", expected, actual)
    }

    @TestFactory
    fun `daily hours for a week`(): List<DynamicTest> = goldenTests("daily_hours_week") { c ->
        val sessions = c.sessions(c.case["sessions"]!!.jsonArray)
        val actual = HistoryPeriodHelper.dailyHoursForWeek(c.noon(c.case.str("anchor")), sessions, c.cal)
        val expected = c.case["hours"]!!.jsonArray
        c.same("hours.size", expected.size, actual.size)
        expected.forEachIndexed { index, value -> c.bits("hours[$index]", value, actual[index]) }
    }
}
