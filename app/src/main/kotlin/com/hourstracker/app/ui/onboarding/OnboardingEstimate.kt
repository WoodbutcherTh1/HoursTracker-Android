package com.hourstracker.app.ui.onboarding

import com.hourstracker.model.DayPayBreakdown
import com.hourstracker.model.IosCalendar
import com.hourstracker.model.OvertimeCalculator
import com.hourstracker.model.WorkplaceSettings

/** The typical week the worker picks. Display only: it never changes pay. */
enum class WeekPattern {
    FiveDays,
    SixDays,
    Varies,
    Custom,
    ;

    /** Weekdays (Foundation numbering: 1 = Sunday ... 7 = Saturday) worked in a typical week. */
    fun workdays(custom: Set<Int>): Set<Int> = when (this) {
        FiveDays -> setOf(1, 2, 3, 4, 5)
        SixDays -> setOf(1, 2, 3, 4, 5, 6)
        Varies -> setOf(1, 2, 3, 4, 5)
        Custom -> custom.ifEmpty { setOf(1, 2, 3, 4, 5) }
    }

    /** Starting value for the weekly-hours slider. */
    fun defaultWeeklyHours(custom: Set<Int>): Int = when (this) {
        FiveDays, SixDays -> 42
        Varies -> 30
        Custom -> OnboardingEstimate.clampWeeklyHours(workdays(custom).size * 8)
    }
}

/** The onboarding's preview numbers, priced by the real pay engine with the typed rate. No pay math of its own. */
object OnboardingEstimate {
    val RATE_RANGE = 1.0..500.0
    val WEEKLY_HOURS_RANGE = 10..60
    const val PREVIEW_DAY_HOURS = 8.0

    fun clampWeeklyHours(hours: Int): Int = hours.coerceIn(WEEKLY_HOURS_RANGE)

    /** Parses the rate field: Western or Arabic-Indic digits, "." or "," or "٫" as the decimal separator. */
    fun parseRate(text: String): Double? {
        val normalized = text.trim().map { char ->
            when {
                char in '٠'..'٩' -> '0' + (char - '٠')
                char == ',' || char == '٫' -> '.'
                else -> char
            }
        }.joinToString("")
        val value = normalized.toDoubleOrNull() ?: return null
        return value.takeIf { it in RATE_RANGE }
    }

    /** "If you work 8 hours today". */
    fun day(settings: WorkplaceSettings, calendar: IosCalendar, hours: Double = PREVIEW_DAY_HOURS): DayPayBreakdown =
        OvertimeCalculator.breakdown(hours, settings, calendar)

    /** A typical week: the weekly hours spread evenly over the workdays, each priced by the engine (ignores weekly caps and holidays). */
    fun weeklyGross(settings: WorkplaceSettings, calendar: IosCalendar, weeklyHours: Int, workdayCount: Int): Double {
        val days = maxOf(1, workdayCount)
        val perDay = weeklyHours.toDouble() / days.toDouble()
        return OvertimeCalculator.breakdown(perDay, settings, calendar).grossPay * days.toDouble()
    }
}
