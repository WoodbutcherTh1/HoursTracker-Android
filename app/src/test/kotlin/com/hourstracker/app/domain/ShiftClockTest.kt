package com.hourstracker.app.domain

import com.hourstracker.model.DayType
import com.hourstracker.model.IosCalendar
import com.hourstracker.model.WorkplaceSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class ShiftClockTest {
    private val zone = ZoneId.of("Asia/Jerusalem")
    private val calendar = IosCalendar(zone, 1, 1, Clock.systemUTC())
    private val settings = WorkplaceSettings(hourlyRate = 100.0)

    // Wednesday 10 June 2026, 08:00 in Israel.
    private val eight = Instant.parse("2026-06-10T05:00:00Z")

    private fun at(minutesAfterEight: Long) = eight.plusSeconds(minutesAfterEight * 60)

    private fun open() = ShiftClock.clockIn(emptyList(), settings, calendar, eight)!!

    @Test
    fun `clocking in opens a shift on today's date`() {
        val record = open()
        assertTrue(record.session.isOpen)
        assertEquals(eight, record.session.clockIn)
        assertEquals(Instant.parse("2026-06-09T21:00:00Z"), record.session.date)
        assertEquals(DayType.Regular, record.session.dayType)
        assertEquals(ShiftClock.active(listOf(record)), record)
    }

    @Test
    fun `a second shift cannot be started while one is running`() {
        assertNull(ShiftClock.clockIn(listOf(open()), settings, calendar, at(5)))
    }

    @Test
    fun `a clock-in time in the future is clamped to now`() {
        val record = ShiftClock.clockIn(emptyList(), settings, calendar, now = eight, at = at(30))!!
        assertEquals(eight, record.session.clockIn)
    }

    @Test
    fun `the weekly rest day is tagged on clock-in`() {
        val saturday = Instant.parse("2026-06-13T05:00:00Z")
        assertEquals(DayType.RestDay, ShiftClock.clockIn(emptyList(), settings, calendar, saturday)!!.session.dayType)
    }

    @Test
    fun `a break can start once and not while another is running`() {
        val onBreak = ShiftClock.startBreak(open(), at(120))!!
        assertTrue(onBreak.session.isOnBreak)
        assertNull(ShiftClock.startBreak(onBreak, at(130)))
    }

    @Test
    fun `ending a break at a workplace that deducts folds it into the break minutes`() {
        val onBreak = ShiftClock.startBreak(open(), at(120))!!
        val back = ShiftClock.endBreak(onBreak, settings, at(150))!!
        assertFalse(back.session.isOnBreak)
        assertEquals(30, back.session.breakMinutes)
    }

    @Test
    fun `at a workplace that pays for breaks the break is kept but not deducted`() {
        val paid = WorkplaceSettings(hourlyRate = 100.0, breaksArePaid = true)
        val onBreak = ShiftClock.startBreak(open(), at(120))!!
        val back = ShiftClock.endBreak(onBreak, paid, at(150))!!
        assertEquals(0, back.session.breakMinutes)
        assertEquals(1, back.session.breaks.size)
    }

    @Test
    fun `ending a break that is not running does nothing`() {
        assertNull(ShiftClock.endBreak(open(), settings, at(10)))
    }

    @Test
    fun `clocking out closes a running break at the same moment`() {
        val onBreak = ShiftClock.startBreak(open(), at(240))!!
        val closed = ShiftClock.clockOut(onBreak, settings, calendar, at(270))
        assertEquals(at(270), closed.session.clockOut)
        assertFalse(closed.session.isOnBreak)
        assertEquals(30, closed.session.breakMinutes)
    }

    @Test
    fun `a shift of six hours or more gets the default break`() {
        val withDefault = WorkplaceSettings(hourlyRate = 100.0, defaultBreakMinutes = 30)
        val closed = ShiftClock.clockOut(open(), withDefault, calendar, at(8 * 60))
        assertEquals(30, closed.session.breakMinutes)
        val short = ShiftClock.clockOut(open(), withDefault, calendar, at(5 * 60))
        assertEquals(0, short.session.breakMinutes)
    }

    @Test
    fun `a recorded break wins over the default one`() {
        val withDefault = WorkplaceSettings(hourlyRate = 100.0, defaultBreakMinutes = 30)
        val onBreak = ShiftClock.startBreak(open(), at(120))!!
        val back = ShiftClock.endBreak(onBreak, withDefault, at(135))!!
        val closed = ShiftClock.clockOut(back, withDefault, calendar, at(8 * 60))
        assertEquals(15, closed.session.breakMinutes)
    }

    @Test
    fun `a shift through the night is flagged as a night shift when it ends`() {
        val night = ShiftClock.clockIn(emptyList(), settings, calendar, Instant.parse("2026-06-10T19:00:00Z"))!! // 22:00 local
        val closed = ShiftClock.clockOut(night, settings, calendar, Instant.parse("2026-06-11T03:00:00Z")) // 06:00 local
        assertTrue(closed.session.isNightShift)
        assertNotNull(closed.session.clockOut)
    }

    @Test
    fun `the record is never changed in place`() {
        val record = open()
        ShiftClock.startBreak(record, at(10))
        ShiftClock.clockOut(record, settings, calendar, at(500))
        assertTrue(record.session.isOpen)
        assertTrue(record.session.breaks.isEmpty())
    }
}
