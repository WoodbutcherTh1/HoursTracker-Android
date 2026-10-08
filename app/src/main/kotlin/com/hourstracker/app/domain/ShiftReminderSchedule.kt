package com.hourstracker.app.domain

import com.hourstracker.model.IosCalendar
import com.hourstracker.model.WorkplaceSettings
import java.time.Instant

/** When the next "your shift starts soon" reminder is due. Pure, so it is easy to test. */
object ShiftReminderSchedule {
    /**
     * The first moment after [now] that is [minutesBefore] before the usual start time on a day that is not a rest day,
     * looking a week ahead; null when every day of the week is a rest day.
     */
    fun next(now: Instant, settings: WorkplaceSettings, minutesBefore: Int, calendar: IosCalendar): Instant? {
        val today = calendar.localDate(now)
        for (offset in 0..7) {
            val date = today.plusDays(offset.toLong())
            val foundationWeekday = date.dayOfWeek.value % 7 + 1
            if (settings.isRestDayWeekday(foundationWeekday)) continue
            val dayStart = date.atStartOfDay(calendar.zone).toInstant()
            val start = calendar.setTime(dayStart, settings.expectedShiftStartHour, settings.expectedShiftStartMinute, 0)
            val trigger = start.minusSeconds(minutesBefore * 60L)
            if (trigger.isAfter(now)) return trigger
        }
        return null
    }
}
