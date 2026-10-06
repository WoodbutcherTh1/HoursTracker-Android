package com.hourstracker.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * `aggregate` (the period totals) has no golden data yet: the Mac harness does not write section E.
 * These tests are derived by hand from the documented rules (weekly overtime, rounding half away
 * from zero). They are NOT a bit-for-bit proof against iOS; see the milestone notes.
 */
class AggregateTest {
    private val zone = ZoneId.of("UTC")

    /** Monday-first, 4 minimal days: the `utc` pinned environment. */
    private val monday = IosCalendar(zone, firstWeekday = 2, minimumDaysInFirstWeek = 4, clock = Clock.fixed(Instant.parse("2026-06-20T00:00:00Z"), zone))

    /** Sunday-first, 1 minimal day: the `il` pinned environment (time zone aside). */
    private val sunday = IosCalendar(zone, firstWeekday = 1, minimumDaysInFirstWeek = 1, clock = Clock.fixed(Instant.parse("2026-06-20T00:00:00Z"), zone))

    private var counter = 0

    private fun shift(day: String, startHour: Int, hours: Int): WorkSession {
        val date = LocalDate.parse(day).atStartOfDay(zone).toInstant()
        val clockIn = date.plusSeconds(startHour * 3600L)
        return WorkSession(
            id = UUID(0, (++counter).toLong()),
            date = date,
            clockIn = clockIn,
            clockOut = clockIn.plusSeconds(hours * 3600L),
        )
    }

    private fun settings(cap: Double = 12.0) = WorkplaceSettings(hourlyRate = 100.0, weeklyOvertimeCapHours = cap)

    /** Mon 8 June to Sat 13 June 2026: six 10 hour shifts in one week. */
    private fun sixTenHourDays() = (8..13).map { shift("2026-06-%02d".format(it), 8, 10) }

    @Test
    fun `weekly overtime promotes regular hours above the weekly standard`() {
        val result = OvertimeCalculator.aggregate(sixTenHourDays(), settings(), monday)

        // Daily: 8.6 regular + 1.4 at 125% each day = 51.6 / 8.4. Weekly excess 60 - 42 = 18, needs 18 - 8.4 = 9.6 more.
        assertEquals(60.0, result.totalHours, 1e-9)
        assertEquals(42.0, result.regularHours, 1e-9)
        assertEquals(18.0, result.ot125Hours, 1e-9)
        assertEquals(0.0, result.ot150Hours, 1e-9)
        assertEquals(4_200.0, result.basePay, 0.0)
        assertEquals(2_250.0, result.ot125Pay, 0.0)
        assertEquals(210.0, result.gasAllowance, 0.0)
        assertEquals(6_660.0, result.totalPay, 0.0)
    }

    @Test
    fun `weekly overtime beyond the cap is paid at 150 percent`() {
        val result = OvertimeCalculator.aggregate(sixTenHourDays(), settings(cap = 5.0), monday)

        assertEquals(42.0, result.regularHours, 1e-9)
        assertEquals(13.4, result.ot125Hours, 1e-9)
        assertEquals(4.6, result.ot150Hours, 1e-9)
        assertEquals(4_200.0, result.basePay, 0.0)
        assertEquals(1_675.0, result.ot125Pay, 0.0)
        assertEquals(690.0, result.ot150Pay, 0.0)
    }

    @Test
    fun `a week at or below the weekly standard is not adjusted`() {
        val sessions = (8..12).map { shift("2026-06-%02d".format(it), 8, 8) }
        val result = OvertimeCalculator.aggregate(sessions, settings(), monday)

        assertEquals(40.0, result.regularHours, 1e-9)
        assertEquals(0.0, result.ot125Hours, 0.0)
        assertEquals(0.0, result.ot150Hours, 0.0)
        assertEquals(4_000.0, result.basePay, 1e-9)
    }

    @Test
    fun `open sessions are ignored`() {
        val open = shift("2026-06-08", 8, 10).also { it.clockOut = null }
        val result = OvertimeCalculator.aggregate(listOf(open), settings(), monday)

        assertEquals(0.0, result.totalHours, 0.0)
        assertEquals(0.0, result.totalPay, 0.0)
    }

    @Test
    fun `weeks are not pooled across a week boundary`() {
        // Thu 4 to Sat 6 June and Mon 8 to Wed 10 June: two Monday-first weeks of three 10 hour days.
        val sessions = listOf(4, 5, 6, 8, 9, 10).map { shift("2026-06-%02d".format(it), 8, 10) }
        val result = OvertimeCalculator.aggregate(sessions, settings(), monday)

        assertEquals(0.0, result.ot150Hours, 0.0)
        assertEquals(6 * 1.4, result.ot125Hours, 1e-9)
    }

    @Test
    fun `week grouping follows the calendar first weekday`() {
        // Sun 7 June and Mon 8 June: one Sunday-first week, two Monday-first weeks.
        assertEquals(sunday.yearAndWeekOfYear(Instant.parse("2026-06-07T08:00:00Z")), sunday.yearAndWeekOfYear(Instant.parse("2026-06-08T08:00:00Z")))
        assertNotEquals(monday.yearAndWeekOfYear(Instant.parse("2026-06-07T08:00:00Z")), monday.yearAndWeekOfYear(Instant.parse("2026-06-08T08:00:00Z")))
        // With one minimal day, the week holding 1 January is week 1 of the new year.
        assertEquals(Pair(2027, 1), sunday.yearAndWeekOfYear(Instant.parse("2026-12-31T08:00:00Z")))
    }

    @Test
    fun `rounding goes half away from zero`() {
        assertEquals(3.0, roundedAwayFromZero(2.5), 0.0)
        assertEquals(-3.0, roundedAwayFromZero(-2.5), 0.0)
        assertEquals(1.0, roundedAwayFromZero(0.5), 0.0)
        assertEquals(0.0, roundedAwayFromZero(0.49999999999999994), 0.0)
        assertEquals(java.lang.Double.doubleToRawLongBits(-0.0), java.lang.Double.doubleToRawLongBits(roundedAwayFromZero(-0.3)))
    }
}
