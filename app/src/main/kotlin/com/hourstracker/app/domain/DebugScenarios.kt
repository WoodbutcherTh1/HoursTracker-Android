package com.hourstracker.app.domain

import com.hourstracker.data.ShiftRecord
import com.hourstracker.model.IosCalendar
import com.hourstracker.model.WorkSession
import java.time.Instant
import java.util.UUID

/** Known data for checking the app by eye. Used by debug builds only. */
object DebugScenarios {
    /**
     * Last week as six 9 hour days (08:00 to 17:00, no break): 54 hours, so the weekly overtime rule applies.
     * Ids are fixed, so loading it again replaces it instead of adding to it.
     */
    class OvertimeWeek(val from: Instant, val to: Instant, val shifts: List<ShiftRecord>)

    fun overtimeWeek(calendar: IosCalendar): OvertimeWeek {
        val thisWeek = calendar.startOfWeekOfYear(calendar.now())
        val start = calendar.addDays(thisWeek, -7)
        val end = calendar.addDays(start, 7)
        val shifts = (0 until 6).map { offset ->
            val day = calendar.addDays(start, offset)
            val clockIn = calendar.setTime(day, 8, 0, 0)
            val clockOut = calendar.setTime(day, 17, 0, 0)
            ShiftRecord(
                session = WorkSession(UUID(7, offset.toLong()), calendar.startOfDay(day), clockIn, clockOut),
                notes = "overtime week",
                isManualEntry = true,
                modifiedAt = clockOut,
            )
        }
        return OvertimeWeek(start, end, shifts)
    }
}
