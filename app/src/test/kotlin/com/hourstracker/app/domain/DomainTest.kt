package com.hourstracker.app.domain

import com.hourstracker.data.ShiftRecord
import com.hourstracker.model.DayType
import com.hourstracker.model.IosCalendar
import com.hourstracker.model.WorkSession
import com.hourstracker.model.WorkplaceSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class DomainTest {
    private val zone = ZoneId.of("Asia/Jerusalem")
    private val now = Instant.parse("2026-06-20T09:00:00Z")
    private val calendar = IosCalendar(zone, firstWeekday = 1, minimumDaysInFirstWeek = 1, clock = Clock.fixed(now, zone))
    private val settings = WorkplaceSettings(hourlyRate = 100.0)

    private fun input(
        dayType: DayType = DayType.Regular,
        direct: Boolean = false,
        hours: Double = 8.0,
        inMinutes: Int = 8 * 60,
        outMinutes: Int = 17 * 60,
        breakMinutes: Int = 0,
    ) = ManualEntryInput(LocalDate.of(2026, 6, 10), dayType, direct, hours, inMinutes, outMinutes, breakMinutes, false, "")

    @Test
    fun `identical clock times are a zero-duration entry`() {
        assertEquals(ManualEntry.Problem.ZeroDuration, ManualEntry.validate(input(inMinutes = 480, outMinutes = 480)))
        assertEquals(ManualEntry.Problem.ZeroDuration, ManualEntry.validate(input(direct = true, hours = 0.0)))
    }

    @Test
    fun `a break that eats the whole shift is rejected`() {
        assertEquals(ManualEntry.Problem.BreakExceedsShift, ManualEntry.validate(input(inMinutes = 480, outMinutes = 540, breakMinutes = 60)))
        assertNull(ManualEntry.validate(input(inMinutes = 480, outMinutes = 540, breakMinutes = 59)))
    }

    @Test
    fun `an overnight shift counts the minutes past midnight`() {
        assertEquals(8 * 60, ManualEntry.enteredShiftMinutes(input(inMinutes = 22 * 60, outMinutes = 6 * 60)))
    }

    @Test
    fun `holiday and sick days need no validation`() {
        assertNull(ManualEntry.validate(input(dayType = DayType.Holiday, inMinutes = 480, outMinutes = 480)))
        assertNull(ManualEntry.validate(input(dayType = DayType.Sick, inMinutes = 480, outMinutes = 480)))
    }

    @Test
    fun `a manual shift keeps its wall-clock times in the device zone`() {
        val record = ManualEntry.build(input(), settings, calendar, now)
        assertEquals(Instant.parse("2026-06-10T05:00:00Z"), record.session.clockIn)
        assertEquals(Instant.parse("2026-06-10T14:00:00Z"), record.session.clockOut)
        assertTrue(record.isManualEntry)
        assertEquals(now, record.modifiedAt)
    }

    @Test
    fun `an overnight manual shift ends the next day`() {
        val record = ManualEntry.build(input(inMinutes = 22 * 60, outMinutes = 6 * 60), settings, calendar, now)
        assertEquals(Instant.parse("2026-06-10T19:00:00Z"), record.session.clockIn)
        assertEquals(Instant.parse("2026-06-11T03:00:00Z"), record.session.clockOut)
    }

    @Test
    fun `total hours start at 08 00`() {
        val record = ManualEntry.build(input(direct = true, hours = 9.5), settings, calendar, now)
        assertEquals(Instant.parse("2026-06-10T05:00:00Z"), record.session.clockIn)
        assertEquals(Instant.parse("2026-06-10T14:30:00Z"), record.session.clockOut)
    }

    @Test
    fun `a sick day has equal clock times and no hours`() {
        val record = ManualEntry.build(input(dayType = DayType.Sick), settings, calendar, now)
        assertEquals(record.session.clockIn, record.session.clockOut)
        assertEquals(0.0, record.session.effectiveHours)
    }

    @Test
    fun `the weekly rest day is tagged automatically`() {
        // 2026-06-13 is a Saturday, the default rest day.
        assertEquals(DayType.RestDay, ManualEntry.automaticDayType(LocalDate.of(2026, 6, 13), settings, emptyList(), calendar))
        assertEquals(DayType.Regular, ManualEntry.automaticDayType(LocalDate.of(2026, 6, 10), settings, emptyList(), calendar))
    }

    @Test
    fun `the sick streak preview counts calendar-adjacent days`() {
        fun sick(day: Int): WorkSession {
            val date = LocalDate.of(2026, 6, day).atStartOfDay(zone).toInstant()
            return WorkSession(UUID(0, day.toLong()), date, date, date, dayType = DayType.Sick)
        }
        val (number, share) = ManualEntry.sickStreakPreview(LocalDate.of(2026, 6, 12), listOf(sick(10), sick(11)), calendar)
        assertEquals(3, number)
        assertEquals(0.5, share)
    }

    @Test
    fun `history totals include every completed shift of the period`() {
        val records = (1..5).map { day ->
            val date = LocalDate.of(2026, 6, day).atStartOfDay(zone).toInstant()
            val clockIn = date.plusSeconds(8 * 3600L)
            ShiftRecord(WorkSession(UUID(0, day.toLong()), date, clockIn, clockIn.plusSeconds(8 * 3600L)))
        }
        val state = HistoryCalculator.build(records, settings, calendar, 0)
        assertEquals(5, state.rows.size)
        assertEquals(40.0, state.totalHours, 1e-9)
        assertTrue(state.totalGross > 0)
        assertTrue(state.totalNet < state.totalGross)
        // Newest first.
        assertTrue(state.rows.first().clockIn > state.rows.last().clockIn)
    }

    @Test
    fun `the previous month holds no shifts of this month`() {
        val date = LocalDate.of(2026, 6, 10).atStartOfDay(zone).toInstant()
        val record = ShiftRecord(WorkSession(UUID(0, 1), date, date.plusSeconds(3600), date.plusSeconds(9 * 3600L)))
        assertEquals(0, HistoryCalculator.build(listOf(record), settings, calendar, -1).rows.size)
    }
}
