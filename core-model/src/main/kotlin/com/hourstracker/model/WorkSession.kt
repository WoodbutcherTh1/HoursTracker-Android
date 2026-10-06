package com.hourstracker.model

import java.time.Instant
import java.util.UUID

/** Pay classification of a work day. Raw names match the iOS `DayType` raw values. */
enum class DayType(val raw: String) {
    Regular("regular"),
    RestDay("restDay"),
    Holiday("holiday"),
    Sick("sick");

    companion object {
        fun fromRaw(raw: String): DayType? = entries.firstOrNull { it.raw == raw }
    }
}

/** One live break taken during a clocked-in shift. `end == null` while the worker is still on that break. */
data class BreakInterval(val start: Instant, val end: Instant?) {
    val isOpen: Boolean get() = end == null

    fun seconds(now: Instant): Double = swiftMax(0.0, (end ?: now).secondsSince(start))
}

class ClockPair(val clockIn: Instant, val clockOut: Instant)

data class WorkSession(
    val id: UUID,
    var date: Instant,
    var clockIn: Instant,
    var clockOut: Instant? = null,
    var breakMinutes: Int = 0,
    var breaks: List<BreakInterval> = emptyList(),
    var dayType: DayType = DayType.Regular,
    var isNightShift: Boolean = false,
) {
    init {
        breakMinutes = maxOf(0, breakMinutes)
    }

    val isOpen: Boolean get() = clockOut == null

    val totalHours: Double
        get() {
            val out = clockOut ?: return 0.0
            return swiftMax(0.0, out.secondsSince(clockIn) / 3600)
        }

    /** Paid hours: total minus the unpaid break. */
    val effectiveHours: Double get() = swiftMax(0.0, totalHours - breakMinutes.toDouble() / 60)

    val activeBreak: BreakInterval? get() = if (isOpen) breaks.lastOrNull { it.isOpen } else null

    val isOnBreak: Boolean get() = activeBreak != null

    fun recordedBreakSeconds(now: Instant): Double = breaks.fold(0.0) { sum, item -> sum + item.seconds(now) }

    /** Applies the workplace's default unpaid break to a long shift that doesn't already carry one. */
    fun applyDefaultBreakIfNeeded(settings: WorkplaceSettings) {
        if (!breaks.isEmpty() || settings.breaksArePaid) return
        if (!(settings.defaultBreakMinutes > 0 && breakMinutes == 0 && totalHours >= 6)) return
        breakMinutes = settings.defaultBreakMinutes
    }

    fun startBreak(at: Instant): Boolean {
        if (!(isOpen && !isOnBreak && at >= clockIn)) return false
        breaks = breaks + BreakInterval(at, null)
        return true
    }

    fun endBreak(at: Instant, now: Instant, deductFromPay: Boolean = true): Boolean {
        val index = breaks.indexOfLast { it.isOpen }
        if (index < 0) return false
        val updated = breaks.toMutableList()
        updated[index] = updated[index].copy(end = maxOf(at, updated[index].start))
        breaks = updated
        if (deductFromPay) syncBreakMinutesFromRecordedBreaks(now)
        return true
    }

    fun closeOpenBreak(at: Instant, now: Instant, deductFromPay: Boolean = true) {
        val index = breaks.indexOfLast { it.isOpen }
        if (index < 0) return
        val updated = breaks.toMutableList()
        updated[index] = updated[index].copy(end = maxOf(at, updated[index].start))
        breaks = updated
        if (deductFromPay) syncBreakMinutesFromRecordedBreaks(now)
    }

    private fun syncBreakMinutesFromRecordedBreaks(now: Instant) {
        if (breaks.isEmpty()) return
        breakMinutes = roundedAwayFromZero(recordedBreakSeconds(now) / 60).toInt()
    }

    /**
     * Paid time elapsed so far: wall-clock time minus recorded breaks (a running break stops the
     * paid clock), unless the workplace pays for breaks.
     */
    fun paidElapsedSeconds(now: Instant, breaksArePaid: Boolean = false): Double {
        val end = clockOut ?: now
        val unpaid = if (breaksArePaid) 0.0 else recordedBreakSeconds(end)
        return swiftMax(0.0, end.secondsSince(clockIn) - unpaid)
    }

    companion object {
        /** Orders clock-in / clock-out correctly (the OCR / RTL swapped-times fix). */
        fun resolveClockPair(clockIn: Instant, clockOut: Instant, calendar: IosCalendar): ClockPair {
            if (clockOut > clockIn) {
                val hours = clockOut.secondsSince(clockIn) / 3600
                if (!(hours > 12)) return ClockPair(clockIn, clockOut)

                val day = calendar.startOfDay(clockIn)
                val inZoned = clockIn.atZone(calendar.zone)
                val outZoned = clockOut.atZone(calendar.zone)
                val earlier = calendar.setTime(day, outZoned.hour, outZoned.minute, 0)
                val later = calendar.setTime(day, inZoned.hour, inZoned.minute, 0)
                if (!(later > earlier)) return ClockPair(clockIn, clockOut)

                val sameDayHours = later.secondsSince(earlier) / 3600
                if (sameDayHours >= 3 && sameDayHours <= 12) return ClockPair(earlier, later)
                return ClockPair(clockIn, clockOut)
            }

            val overnightOut = calendar.addDays(clockOut, 1)
            val overnightHours = overnightOut.secondsSince(clockIn) / 3600
            val swappedSameDayHours = clockIn.secondsSince(clockOut) / 3600

            if (overnightHours > 12 && swappedSameDayHours >= 3 && swappedSameDayHours <= 12) {
                return ClockPair(clockOut, clockIn)
            }
            return ClockPair(clockIn, overnightOut)
        }

        /** A shift is a night shift when at least two hours fall between 22:00 and 06:00 (22:00 plus 8 elapsed hours). */
        fun qualifiesAsNightShift(clockIn: Instant, clockOut: Instant, calendar: IosCalendar): Boolean {
            if (!(clockOut > clockIn)) return false
            var nightSeconds = 0.0
            var day = calendar.addDays(calendar.startOfDay(clockIn), -1)
            while (day <= clockOut) {
                val windowStart = calendar.setTime(day, 22, 0, 0)
                val windowEnd = windowStart.plusSeconds(8.0 * 3600)
                val overlap = minOf(clockOut, windowEnd).secondsSince(maxOf(clockIn, windowStart))
                if (overlap > 0) nightSeconds += overlap
                day = calendar.addDays(day, 1)
            }
            return nightSeconds >= 2.0 * 3600
        }
    }
}
