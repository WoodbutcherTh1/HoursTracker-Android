package com.hourstracker.app.domain

import com.hourstracker.data.ShiftRecord
import com.hourstracker.model.IosCalendar
import com.hourstracker.model.WorkSession
import com.hourstracker.model.WorkplaceSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class HistoryFilterTest {
    private val zone = ZoneId.of("UTC")
    // Wednesday 17 June 2026; weeks start on Sunday.
    private val calendar = IosCalendar(zone, 1, 1, Clock.fixed(Instant.parse("2026-06-17T10:30:00Z"), zone))
    private val settings = WorkplaceSettings(hourlyRate = 50.0)
    private var n = 0L

    private fun shift(day: String) = ShiftRecord(
        WorkSession(UUID(1, ++n), Instant.parse("${day}T00:00:00Z"), Instant.parse("${day}T08:00:00Z"), Instant.parse("${day}T16:00:00Z")),
    )

    private val records = listOf(
        shift("2026-06-17"), shift("2026-06-15"), shift("2026-06-10"), shift("2026-06-02"),
        shift("2026-05-20"), shift("2026-01-05"), shift("2025-12-30"),
    )

    private fun count(filter: HistoryFilter, offset: Int = 0) = HistoryCalculator.build(records, settings, calendar, offset, filter).rows.size

    @Test
    fun `this week lists Sunday to Saturday around today`() = assertEquals(2, count(HistoryFilter.Week))

    @Test
    fun `last week is one week back`() = assertEquals(1, count(HistoryFilter.Week, -1))

    @Test
    fun `this month is the calendar month`() = assertEquals(4, count(HistoryFilter.Month))

    @Test
    fun `last month is May`() = assertEquals(1, count(HistoryFilter.Month, -1))

    @Test
    fun `payroll starting on the 1st matches the calendar month`() = assertEquals(count(HistoryFilter.Month), count(HistoryFilter.Payroll))

    @Test
    fun `payroll starting mid-month follows the payroll window`() {
        val mid = WorkplaceSettings(hourlyRate = 50.0, payrollStartDay = 10)
        val rows = HistoryCalculator.build(records, mid, calendar, 0, HistoryFilter.Payroll).rows
        assertEquals(3, rows.size) // 10 June to 9 July: the 10th, 15th and 17th
    }

    @Test
    fun `this year excludes last year`() = assertEquals(6, count(HistoryFilter.Year))

    @Test
    fun `last year holds the December shift`() = assertEquals(1, count(HistoryFilter.Year, -1))

    @Test
    fun `all lists every finished shift`() = assertEquals(7, count(HistoryFilter.All))

    @Test
    fun `totals follow the filter`() {
        val week = HistoryCalculator.build(records, settings, calendar, 0, HistoryFilter.Week)
        assertEquals(16.0, week.totalHours, 1e-9)
    }
}
