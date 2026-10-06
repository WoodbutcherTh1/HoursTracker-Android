package com.hourstracker.model

import java.time.Instant

enum class MaritalStatus(val raw: String) {
    Single("single"),
    Married("married");

    companion object {
        fun fromRaw(raw: String): MaritalStatus? = entries.firstOrNull { it.raw == raw }
    }
}

/**
 * The pay-relevant part of the iOS `WorkplaceSettings`. Like the Swift initializer and decoder,
 * constructing one clamps every validated field ([normalizeValidatedFields]).
 */
data class WorkplaceSettings(
    var hourlyRate: Double,
    var dailyGasAllowance: Double = 35.0,
    var standardDayHours: Double = 8.6,
    var ot125HoursCap: Double = 2.0,
    var locationRadiusMeters: Double = 150.0,
    var maritalStatus: MaritalStatus = MaritalStatus.Single,
    var hasChildren: Boolean = false,
    var numberOfChildren: Int = 0,
    var spouseEmployed: Boolean = false,
    /** Start of the birth day in the device time zone; only used for the retirement-age NI rate. */
    var birthDate: Instant? = null,
    var payrollStartDay: Int = 1,
    var restDayWeekday: Int = 7,
    var secondRestDayWeekday: Int? = null,
    var defaultBreakMinutes: Int = 0,
    var breaksArePaid: Boolean = false,
    var nightStandardDayHours: Double = 7.0,
    var weeklyStandardHours: Double = 42.0,
    var weeklyOvertimeCapHours: Double = 12.0,
    var currencyCode: String = "ILS",
    var expectedShiftStartHour: Int = 8,
    var expectedShiftStartMinute: Int = 0,
) {
    init {
        normalizeValidatedFields()
    }

    val creditPoints: Double get() = TaxCreditPointsCalculator.creditPoints(this)

    /** Whole-years age as of now, if `birthDate` is set. */
    fun age(calendar: IosCalendar): Int? {
        val born = birthDate ?: return null
        return calendar.wholeYears(born, calendar.now())
    }

    /** `true` once the worker has reached retirement age. An unknown birth date is treated as not retired. */
    fun hasReachedRetirementAge(calendar: IosCalendar): Boolean {
        val years = age(calendar) ?: return false
        return years >= RETIREMENT_AGE
    }

    val restDayWeekdays: Set<Int>
        get() {
            val days = mutableSetOf(restDayWeekday)
            secondRestDayWeekday?.let { days.add(it) }
            return days
        }

    fun isRestDayWeekday(weekday: Int): Boolean = restDayWeekdays.contains(weekday)

    /** Single source of truth for numeric bounds (construction and decoding). */
    fun normalizeValidatedFields() {
        hourlyRate = swiftMax(0.0, hourlyRate)
        dailyGasAllowance = swiftMax(0.0, dailyGasAllowance)
        standardDayHours = swiftMin(24.0, swiftMax(0.1, standardDayHours))
        ot125HoursCap = swiftMax(0.0, ot125HoursCap)
        locationRadiusMeters = swiftMin(2_000.0, swiftMax(50.0, locationRadiusMeters))
        numberOfChildren = minOf(15, maxOf(0, numberOfChildren))
        payrollStartDay = HistoryPeriodHelper.normalizedStartDay(payrollStartDay)
        restDayWeekday = minOf(maxOf(restDayWeekday, 1), 7)
        secondRestDayWeekday?.let { second ->
            val clamped = minOf(maxOf(second, 1), 7)
            secondRestDayWeekday = if (clamped == restDayWeekday) null else clamped
        }
        defaultBreakMinutes = maxOf(0, defaultBreakMinutes)
        nightStandardDayHours = swiftMin(24.0, swiftMax(0.1, nightStandardDayHours))
        weeklyStandardHours = swiftMin(60.0, swiftMax(1.0, weeklyStandardHours))
        weeklyOvertimeCapHours = swiftMin(24.0, swiftMax(0.0, weeklyOvertimeCapHours))
        expectedShiftStartHour = minOf(maxOf(expectedShiftStartHour, 0), 23)
        expectedShiftStartMinute = minOf(maxOf(expectedShiftStartMinute, 0), 59)
    }

    /** Auto-filled clock-in/out for a day the worker is marked as not actually working. */
    fun expectedShift(day: Instant, calendar: IosCalendar): ClockPair {
        val dayStart = calendar.startOfDay(day)
        val clockIn = calendar.setTime(dayStart, expectedShiftStartHour, expectedShiftStartMinute, 0)
        val clockOut = clockIn.plusSeconds((standardDayHours * 3600) + (defaultBreakMinutes * 60).toDouble())
        return ClockPair(clockIn, clockOut)
    }

    companion object {
        /** Israeli retirement age, simplified to one figure. */
        const val RETIREMENT_AGE = 67
    }
}
