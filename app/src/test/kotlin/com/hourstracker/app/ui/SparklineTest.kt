package com.hourstracker.app.ui

import com.hourstracker.app.ui.home.components.calculateDailyHours
import com.hourstracker.data.ShiftRecord
import com.hourstracker.model.BreakInterval
import com.hourstracker.model.IosCalendar
import com.hourstracker.model.WorkSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class SparklineTest {
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-06-17T10:30:00Z")
    private val calendar = IosCalendar(zone, 1, 1, Clock.fixed(now, zone))

    private fun finished(day: String, from: String, to: String, id: Long) = ShiftRecord(
        WorkSession(UUID(0, id), Instant.parse("${day}T00:00:00Z"), Instant.parse("${day}T${from}:00Z"), Instant.parse("${day}T${to}:00Z")),
    )

    @Test
    fun `seven points, oldest first, today last`() {
        val hours = calculateDailyHours(listOf(finished("2026-06-17", "08:00", "09:00", 1), finished("2026-06-11", "08:00", "12:00", 2)), calendar, now)
        assertEquals(7, hours.size)
        assertEquals(1.0, hours[6], 1e-9)
        assertEquals(4.0, hours[0], 1e-9)
        assertEquals(0.0, hours[3], 1e-9)
    }

    @Test
    fun `a new shift changes the points`() {
        val before = calculateDailyHours(emptyList(), calendar, now)
        val after = calculateDailyHours(listOf(finished("2026-06-16", "08:00", "16:30", 1)), calendar, now)
        assertEquals(0.0, before[5], 1e-9)
        assertEquals(8.5, after[5], 1e-9)
    }

    @Test
    fun `a running shift counts what it has earned so far, without its break`() {
        val start = Instant.parse("2026-06-17T08:00:00Z")
        val running = ShiftRecord(
            WorkSession(UUID(0, 9), Instant.parse("2026-06-17T00:00:00Z"), start, null, 0, listOf(BreakInterval(Instant.parse("2026-06-17T09:00:00Z"), Instant.parse("2026-06-17T09:30:00Z")))),
        )
        assertEquals(2.0, calculateDailyHours(listOf(running), calendar, now)[6], 1e-9)
        // Half an hour later the point has grown by half an hour.
        assertEquals(2.5, calculateDailyHours(listOf(running), calendar, now.plusSeconds(1800))[6], 1e-9)
    }
}
