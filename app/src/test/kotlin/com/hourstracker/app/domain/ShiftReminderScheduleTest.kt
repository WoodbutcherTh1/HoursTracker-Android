package com.hourstracker.app.domain

import com.hourstracker.model.IosCalendar
import com.hourstracker.model.WorkplaceSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class ShiftReminderScheduleTest {
    private val zone = ZoneId.of("UTC")
    private fun calendarAt(now: String) = IosCalendar(zone, 1, 1, Clock.fixed(Instant.parse(now), zone))

    // Wednesday 17 June 2026. Usual start 08:00. Rest day: Saturday (foundation weekday 7).
    private fun settings(rest: Int = 7, second: Int? = null) = WorkplaceSettings(
        hourlyRate = 50.0,
        restDayWeekday = rest,
        secondRestDayWeekday = second,
        expectedShiftStartHour = 8,
        expectedShiftStartMinute = 0,
    )

    @Test
    fun `before the reminder time the next reminder is today`() {
        val now = "2026-06-17T06:00:00Z"
        assertEquals(Instant.parse("2026-06-17T07:45:00Z"), ShiftReminderSchedule.next(Instant.parse(now), settings(), 15, calendarAt(now)))
    }

    @Test
    fun `after the reminder time the next reminder is tomorrow`() {
        val now = "2026-06-17T07:50:00Z"
        assertEquals(Instant.parse("2026-06-18T07:45:00Z"), ShiftReminderSchedule.next(Instant.parse(now), settings(), 15, calendarAt(now)))
    }

    @Test
    fun `a rest day is skipped`() {
        // Friday evening: Saturday is the rest day, so the next reminder is Sunday.
        val now = "2026-06-19T20:00:00Z"
        assertEquals(Instant.parse("2026-06-21T07:30:00Z"), ShiftReminderSchedule.next(Instant.parse(now), settings(), 30, calendarAt(now)))
    }

    @Test
    fun `two rest days are both skipped`() {
        // Thursday evening, Friday (6) and Saturday (7) are rest days: next is Sunday.
        val now = "2026-06-18T20:00:00Z"
        assertEquals(Instant.parse("2026-06-21T07:45:00Z"), ShiftReminderSchedule.next(Instant.parse(now), settings(rest = 6, second = 7), 15, calendarAt(now)))
    }

    @Test
    fun `a bigger lead time moves the reminder earlier`() {
        val now = "2026-06-17T05:00:00Z"
        assertEquals(Instant.parse("2026-06-17T06:00:00Z"), ShiftReminderSchedule.next(Instant.parse(now), settings(), 120, calendarAt(now)))
    }

    @Test
    fun `the week after next is never searched past a week`() {
        // The rest day is Sunday: from Saturday evening the next working day is Monday.
        val now = "2026-06-20T20:00:00Z"
        assertEquals(Instant.parse("2026-06-22T07:45:00Z"), ShiftReminderSchedule.next(Instant.parse(now), settings(rest = 1), 15, calendarAt(now)))
    }
}
