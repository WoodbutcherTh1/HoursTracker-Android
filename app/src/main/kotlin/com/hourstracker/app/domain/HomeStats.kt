package com.hourstracker.app.domain

import com.hourstracker.data.ShiftRecord
import com.hourstracker.model.IosCalendar
import java.time.Instant

/** The three numbers on Home: hours today, hours this week, and the number of shifts this month. Completed shifts only. */
data class HomeStats(val todayHours: Double, val weekHours: Double, val monthShiftCount: Int) {
    companion object {
        fun compute(records: List<ShiftRecord>, calendar: IosCalendar, now: Instant): HomeStats {
            val completed = records.map { it.session }.filter { it.clockOut != null }
            val today = calendar.startOfDay(now)
            val weekStart = calendar.startOfWeekOfYear(now)
            val weekEnd = calendar.addDays(weekStart, 7)
            val nowDate = calendar.localDate(now)

            val todayHours = completed.filter { calendar.isSameDay(it.date, today) }.fold(0.0) { sum, s -> sum + s.totalHours }
            val weekHours = completed.filter { it.date >= weekStart && it.date < weekEnd }.fold(0.0) { sum, s -> sum + s.totalHours }
            val monthCount = completed.count {
                val day = calendar.localDate(it.date)
                day.year == nowDate.year && day.month == nowDate.month
            }
            return HomeStats(todayHours, weekHours, monthCount)
        }
    }
}
