package com.hourstracker.app.data

import com.hourstracker.data.ShiftRecord
import com.hourstracker.model.DayType
import com.hourstracker.model.IosCalendar
import com.hourstracker.model.WorkSession
import java.time.DayOfWeek
import java.time.Instant
import java.util.UUID

/** Mock history for M2a (debug and design review only): about three months of plausible shifts. */
object MockShifts {
    fun build(calendar: IosCalendar): List<ShiftRecord> {
        val today = calendar.localDate(calendar.now())
        val records = ArrayList<ShiftRecord>()
        var counter = 0L
        for (back in 1..90) {
            val day = today.minusDays(back.toLong())
            if (day.dayOfWeek == DayOfWeek.SATURDAY) continue
            // A few longer days, one night shift a week, a sick streak and a holiday for variety.
            val night = day.dayOfWeek == DayOfWeek.WEDNESDAY && back % 2 == 0
            val hours = if (day.dayOfWeek == DayOfWeek.FRIDAY) 5.0 else if (back % 5 == 0) 10.5 else 8.75
            val startHour = if (night) 22 else 8
            val dayStart = day.atStartOfDay(calendar.zone).toInstant()
            val clockIn = calendar.setTime(dayStart, startHour, 0, 0)
            val dayType = when {
                back in 20..22 -> DayType.Sick
                back == 40 -> DayType.Holiday
                else -> DayType.Regular
            }
            val clockOut = Instant.ofEpochSecond(clockIn.epochSecond + (hours * 3600).toLong())
            records += ShiftRecord(
                session = WorkSession(
                    id = UUID(0, ++counter),
                    date = dayStart,
                    clockIn = clockIn,
                    clockOut = clockOut,
                    breakMinutes = if (hours >= 8) 30 else 0,
                    dayType = dayType,
                    isNightShift = night,
                ),
                isManualEntry = back % 3 == 0,
                modifiedAt = clockOut,
            )
        }
        return records
    }
}
