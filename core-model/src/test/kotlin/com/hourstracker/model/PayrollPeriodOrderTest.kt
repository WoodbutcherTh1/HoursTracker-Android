package com.hourstracker.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class PayrollPeriodOrderTest {
    private val zone = ZoneId.of("Asia/Jerusalem")
    private val cal = IosCalendar(zone, firstWeekday = 1, minimumDaysInFirstWeek = 1)

    @Test
    fun `period start is before end for every start day and month`() {
        for (month in 1..12) {
            val anchor = cal.dateFrom(2026, month, 15)
            for (startDay in 1..28) {
                val period = HistoryPeriodHelper.payrollPeriodForMonthAnchor(anchor, startDay, cal)
                val start = cal.localDate(period.start)
                val end = cal.localDate(period.end)
                assertTrue(start.isBefore(end), "start $start must be before end $end (month $month, startDay $startDay)")
                assertEquals(startDay, start.dayOfMonth)
            }
        }
    }

    @Test
    fun `start day 1 spans the whole calendar month`() {
        val period = HistoryPeriodHelper.payrollPeriodForMonthAnchor(cal.dateFrom(2026, 10, 9), 1, cal)
        assertEquals(LocalDate.of(2026, 10, 1), cal.localDate(period.start))
        assertEquals(LocalDate.of(2026, 10, 31), cal.localDate(period.end))
    }
}
