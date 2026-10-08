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
import java.util.Locale
import java.util.UUID

class HistorySearchTest {
    private val zone = ZoneId.of("UTC")
    private val calendar = IosCalendar(zone, 1, 1, Clock.fixed(Instant.parse("2026-06-17T10:30:00Z"), zone))
    private val settings = WorkplaceSettings(hourlyRate = 50.0)
    private var n = 0L

    private fun shift(day: String, from: String, to: String) = ShiftRecord(
        WorkSession(UUID(2, ++n), Instant.parse("${day}T00:00:00Z"), Instant.parse("${day}T${from}:00Z"), Instant.parse("${day}T${to}:00Z")),
    )

    // Wed 17 June: 4 h; Mon 15 June: 8 h; Fri 12 June: 2.5 h (the only one with that length).
    private val records = listOf(shift("2026-06-17", "08:00", "12:00"), shift("2026-06-15", "08:00", "16:00"), shift("2026-06-12", "08:00", "10:30"))

    private fun rows(query: String) = HistoryCalculator.build(records, settings, calendar, 0, HistoryFilter.Month, query, Locale.ENGLISH)

    @Test
    fun `an empty search shows everything`() = assertEquals(3, rows("").rows.size)

    @Test
    fun `a blank search shows everything`() = assertEquals(3, rows("   ").rows.size)

    @Test
    fun `day and month with a slash finds the day`() = assertEquals(1, rows("15/06").rows.size)

    @Test
    fun `month first spelling works too`() = assertEquals(1, rows("06/12").rows.size)

    @Test
    fun `a full ISO date works`() = assertEquals(1, rows("2026-06-17").rows.size)

    @Test
    fun `a weekday name finds that day`() = assertEquals(1, rows("monday").rows.size)

    @Test
    fun `the month name finds every shift of the month`() = assertEquals(3, rows("june").rows.size)

    @Test
    fun `an amount finds the shift that earned it, with a dot or a comma`() {
        val gross = rows("").rows.first { it.hours == 2.5 }.gross
        val dot = String.format(Locale.ROOT, "%.2f", gross)
        assertEquals(1, rows(dot).rows.size)
        assertEquals(1, rows(dot.replace('.', ',')).rows.size)
    }

    @Test
    fun `an unknown word finds nothing`() = assertEquals(0, rows("xyz").rows.size)

    @Test
    fun `totals follow the search`() {
        val found = rows("12/06")
        assertEquals(1, found.rows.size)
        assertEquals(2.5, found.totalHours, 1e-9)
    }
}
