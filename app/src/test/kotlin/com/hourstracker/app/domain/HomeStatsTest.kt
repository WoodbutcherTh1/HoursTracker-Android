package com.hourstracker.app.domain

import com.hourstracker.data.ShiftRecord
import com.hourstracker.model.IosCalendar
import com.hourstracker.model.WorkSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class HomeStatsTest {
    private val zone = ZoneId.of("Asia/Jerusalem")
    private val calendar = IosCalendar(zone, 1, 1, Clock.systemUTC())

    // Wednesday 10 June 2026 noon in Israel. The week runs Sunday 7 June to Saturday 13 June.
    private val now = Instant.parse("2026-06-10T09:00:00Z")
    private var n = 0L

    private fun shift(day: LocalDate, hours: Long, open: Boolean = false): ShiftRecord {
        val date = day.atStartOfDay(zone).toInstant()
        val clockIn = date.plusSeconds(8 * 3600)
        return ShiftRecord(WorkSession(UUID(0, ++n), date, clockIn, if (open) null else clockIn.plusSeconds(hours * 3600)))
    }

    @Test
    fun `today counts only shifts of today and ignores a running shift`() {
        val stats = HomeStats.compute(
            listOf(shift(LocalDate.of(2026, 6, 10), 4), shift(LocalDate.of(2026, 6, 10), 2), shift(LocalDate.of(2026, 6, 9), 8), shift(LocalDate.of(2026, 6, 10), 0, open = true)),
            calendar, now,
        )
        assertEquals(6.0, stats.todayHours, 1e-9)
    }

    @Test
    fun `the week follows the first weekday of the calendar`() {
        val records = listOf(
            shift(LocalDate.of(2026, 6, 6), 8), // Saturday before: previous week
            shift(LocalDate.of(2026, 6, 7), 8), // Sunday: this week when weeks start on Sunday
            shift(LocalDate.of(2026, 6, 13), 8), // Saturday: this week
            shift(LocalDate.of(2026, 6, 14), 8), // Sunday after: next week when weeks start on Sunday
        )
        assertEquals(16.0, HomeStats.compute(records, calendar, now).weekHours, 1e-9)

        // The same days, counted by a calendar whose week starts on Monday: Sunday 7 June closes the previous week.
        val monday = IosCalendar(zone, 2, 4, Clock.systemUTC())
        val sundayAndMonday = listOf(shift(LocalDate.of(2026, 6, 7), 8), shift(LocalDate.of(2026, 6, 8), 3))
        assertEquals(11.0, HomeStats.compute(sundayAndMonday, calendar, now).weekHours, 1e-9)
        assertEquals(3.0, HomeStats.compute(sundayAndMonday, monday, now).weekHours, 1e-9)
    }

    @Test
    fun `the month counts shifts of the calendar month`() {
        val records = listOf(shift(LocalDate.of(2026, 5, 31), 8), shift(LocalDate.of(2026, 6, 1), 8), shift(LocalDate.of(2026, 6, 30), 8), shift(LocalDate.of(2026, 7, 1), 8))
        assertEquals(2, HomeStats.compute(records, calendar, now).monthShiftCount)
    }
}
