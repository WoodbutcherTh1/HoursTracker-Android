package com.hourstracker.app.domain

import com.hourstracker.data.ShiftRecord
import com.hourstracker.model.IosCalendar
import com.hourstracker.model.WorkSession
import com.hourstracker.model.WorkplaceSettings
import java.time.Instant
import java.util.UUID

/**
 * Clocking in and out, and breaks: the rules of the iOS app. Every function returns the new record (or null when the
 * action does not apply) and never changes the record it is given.
 */
object ShiftClock {
    /** The running shift, if any: the one without a clock-out. */
    fun active(shifts: List<ShiftRecord>): ShiftRecord? = shifts.firstOrNull { it.session.isOpen }

    /** Starts a shift. [at] is clamped so it cannot be in the future. Returns null when a shift is already running. */
    fun clockIn(
        existing: List<ShiftRecord>,
        settings: WorkplaceSettings,
        calendar: IosCalendar,
        now: Instant,
        at: Instant = now,
        id: UUID = UUID.randomUUID(),
        workplaceId: UUID? = null,
    ): ShiftRecord? {
        if (active(existing) != null) return null
        val clockIn = minOf(at, now)
        val date = calendar.startOfDay(clockIn)
        val dayType = ManualEntry.automaticDayType(calendar.localDate(clockIn), settings, existing.map { it.session }, calendar)
        return ShiftRecord(
            session = WorkSession(id = id, date = date, clockIn = clockIn, clockOut = null, dayType = dayType),
            modifiedAt = now,
            workplaceId = workplaceId,
        )
    }

    /** Starts a break. Not possible on a closed shift, while a break is running, or before the clock-in. */
    fun startBreak(record: ShiftRecord, now: Instant, at: Instant = now): ShiftRecord? {
        val session = record.session.copy()
        if (!session.startBreak(minOf(at, now))) return null
        return record.copy(session = session, modifiedAt = now)
    }

    /**
     * Ends the running break. A workplace that deducts breaks folds the recorded total into the break minutes; one
     * that pays for them keeps the break for the record only.
     */
    fun endBreak(record: ShiftRecord, settings: WorkplaceSettings, now: Instant, at: Instant = now): ShiftRecord? {
        val session = record.session.copy()
        if (!session.endBreak(minOf(at, now), now, deductFromPay = !settings.breaksArePaid)) return null
        return record.copy(session = session, modifiedAt = now)
    }

    /**
     * Closes the shift: a running break ends at the same moment, the night-shift flag is set from the hours actually
     * worked, and a long shift without a recorded break gets the workplace's default unpaid break.
     */
    fun clockOut(record: ShiftRecord, settings: WorkplaceSettings, calendar: IosCalendar, now: Instant): ShiftRecord {
        val session = record.session.copy()
        session.closeOpenBreak(now, now, deductFromPay = !settings.breaksArePaid)
        session.clockOut = now
        session.isNightShift = WorkSession.qualifiesAsNightShift(session.clockIn, now, calendar)
        session.applyDefaultBreakIfNeeded(settings)
        return record.copy(session = session, modifiedAt = now)
    }
}
