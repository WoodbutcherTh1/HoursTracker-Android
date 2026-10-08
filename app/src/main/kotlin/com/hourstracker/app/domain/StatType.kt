package com.hourstracker.app.domain

import com.hourstracker.app.R

/** Types of stats that can be displayed in StatCards */
enum class StatType {
    TODAY,
    WEEK,
    MONTH
}

/** UI metadata for each stat type */
object StatTypeMetadata {
    fun titleResId(type: StatType): Int = when (type) {
        StatType.TODAY -> R.string.home_stat_today_short
        StatType.WEEK -> R.string.home_stat_week_short
        StatType.MONTH -> R.string.home_stat_month_short
    }

    fun accessibilityTitleResId(type: StatType): Int = when (type) {
        StatType.TODAY -> R.string.home_stat_today
        StatType.WEEK -> R.string.home_stat_week
        StatType.MONTH -> R.string.home_stat_month
    }

    fun formatValue(type: StatType, stats: HomeStats): String = when (type) {
        StatType.TODAY -> com.hourstracker.model.HistoryPeriodHelper.formatHoursClock(stats.todayHours)
        StatType.WEEK -> com.hourstracker.model.HistoryPeriodHelper.formatHoursClock(stats.weekHours)
        StatType.MONTH -> stats.monthShiftCount.toString()
    }
}
