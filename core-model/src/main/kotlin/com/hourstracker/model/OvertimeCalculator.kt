package com.hourstracker.model

import java.time.Instant

data class DayPayBreakdown(
    /** Base-rate hours: 100% on regular days, 150% on rest days / holidays. */
    val regularHours: Double,
    /** First overtime tier: 125% on regular days, 175% on rest days / holidays. */
    val ot125Hours: Double,
    /** Second overtime tier: 150% on regular days, 200% on rest days / holidays. */
    val ot150Hours: Double,
    val totalHours: Double,
    val gasAllowance: Double,
    val basePay: Double,
    val ot125Pay: Double,
    val ot150Pay: Double,
    /** Gross pay including the gas allowance. */
    val totalPay: Double,
    /** Estimated net pay. */
    val netPay: Double,
    val incomeTax: Double,
    val nationalInsurance: Double,
    val healthTax: Double,
    val creditPointsApplied: Double,
    val creditPoints: Double,
    val currencyCode: String,
) {
    val grossPay: Double get() = totalPay
}

data class SessionBreakdown(val session: WorkSession, val breakdown: DayPayBreakdown)

/**
 * Literal port of the iOS pay engine. Operation order, rounding and `Double` behavior are the
 * same as in Swift; do not "improve" anything here.
 *
 * Wherever iOS leans on `Calendar.current` (a default parameter), [calendar] is that device
 * calendar; [aggregate] takes a separate [currentCalendar] for the places that ignore its
 * `calendar` parameter on iOS.
 */
object OvertimeCalculator {
    class RateTiers(val base: Double, val tier1: Double, val tier2: Double)

    fun tiers(dayType: DayType): RateTiers = when (dayType) {
        DayType.Regular -> RateTiers(1.0, 1.25, 1.5)
        DayType.RestDay, DayType.Holiday -> RateTiers(1.5, 1.75, 2.0)
        DayType.Sick -> RateTiers(0.0, 0.0, 0.0)
    }

    /** 1-indexed position within a run of consecutive calendar days all marked sick. */
    fun sickStreakDayNumber(date: Instant, sickDates: Set<Instant>, calendar: IosCalendar): Int {
        var count = 1
        var cursor = calendar.startOfDay(date)
        while (true) {
            val previous = calendar.addDays(cursor, -1)
            if (!sickDates.contains(calendar.startOfDay(previous))) break
            count += 1
            cursor = previous
        }
        return count
    }

    /** Israeli sick-pay schedule: day 1 unpaid, days 2-3 half pay, day 4+ full pay. */
    fun sickPayPercentage(streakDayNumber: Int): Double = when {
        streakDayNumber <= 1 -> 0.0
        streakDayNumber == 2 || streakDayNumber == 3 -> 0.5
        else -> 1.0
    }

    fun sickStreakDayNumbers(sessions: List<WorkSession>, calendar: IosCalendar): Map<Instant, Int> {
        val sickDates = sessions.filter { it.dayType == DayType.Sick }.map { calendar.startOfDay(it.date) }.toSet()
        val result = LinkedHashMap<Instant, Int>()
        for (date in sickDates) {
            result[date] = sickStreakDayNumber(date, sickDates, calendar)
        }
        return result
    }

    fun standardHours(session: WorkSession, settings: WorkplaceSettings): Double =
        if (session.isNightShift) settings.nightStandardDayHours else settings.standardDayHours

    /** Simple regular-day breakdown from a raw hours total. */
    fun breakdown(
        totalHours: Double,
        settings: WorkplaceSettings,
        calendar: IosCalendar,
        includeGasAllowance: Boolean = true,
    ): DayPayBreakdown {
        val regular = swiftMin(totalHours, settings.standardDayHours)
        val overtimeTotal = swiftMax(0.0, totalHours - settings.standardDayHours)
        val ot125 = swiftMin(overtimeTotal, settings.ot125HoursCap)
        val ot150 = swiftMax(0.0, overtimeTotal - ot125)

        val rate = settings.hourlyRate
        val gas = if (includeGasAllowance) settings.dailyGasAllowance else 0.0
        val basePay = regular * rate
        val ot125Pay = ot125 * rate * 1.25
        val ot150Pay = ot150 * rate * 1.5
        val gross = basePay + ot125Pay + ot150Pay + gas

        val tax = IsraeliTaxEstimator.estimateDailyNet(gross, settings, calendar)
        val points = TaxCreditPointsCalculator.creditPoints(settings)

        return DayPayBreakdown(
            regularHours = regular,
            ot125Hours = ot125,
            ot150Hours = ot150,
            totalHours = totalHours,
            gasAllowance = gas,
            basePay = basePay,
            ot125Pay = ot125Pay,
            ot150Pay = ot150Pay,
            totalPay = gross,
            netPay = tax.net,
            incomeTax = tax.incomeTax,
            nationalInsurance = tax.nationalInsurance,
            healthTax = tax.healthTax,
            creditPointsApplied = tax.creditApplied,
            creditPoints = points,
            currencyCode = settings.currencyCode,
        )
    }

    private class Slice(
        val session: WorkSession,
        val base: Double,
        val tier1: Double,
        val tier2: Double,
        val hours: Double,
        val gas: Double,
        val basePay: Double,
        val tier1Pay: Double,
        val tier2Pay: Double,
        val gross: Double,
    )

    /**
     * Day-aware breakdowns for the sessions of ONE calendar day: processed in clock-in order,
     * sharing one daily tier allowance; the daily gas allowance is paid once.
     */
    fun breakdowns(
        daySessions: List<WorkSession>,
        settings: WorkplaceSettings,
        calendar: IosCalendar,
        sickStreakDayNumbers: Map<Instant, Int> = emptyMap(),
    ): List<SessionBreakdown> {
        if (daySessions.isEmpty()) return emptyList()

        val ordered = daySessions.sortedBy { it.clockIn }
        val rate = settings.hourlyRate
        var consumed = 0.0
        var gasRemaining = settings.dailyGasAllowance
        val slices = ArrayList<Slice>()

        for (session in ordered) {
            val isSick = session.dayType == DayType.Sick
            val hours = if (isSick) 0.0 else session.effectiveHours
            val standard = standardHours(session, settings)
            val tier1Boundary = standard
            val tier2Boundary = standard + settings.ot125HoursCap

            val start = consumed
            val end = start + hours
            val base = swiftMax(0.0, swiftMin(end, tier1Boundary) - start)
            val tier1 = swiftMax(0.0, swiftMin(end, tier2Boundary) - swiftMax(start, tier1Boundary))
            val tier2 = swiftMax(0.0, end - swiftMax(start, tier2Boundary))
            consumed = end

            val gas: Double
            if (hours > 0 && gasRemaining > 0) {
                gas = gasRemaining
                gasRemaining = 0.0
            } else {
                gas = 0.0
            }

            val basePay: Double
            val tier1Pay: Double
            val tier2Pay: Double
            if (isSick) {
                val dayKey = calendar.startOfDay(session.date)
                val streakNumber = sickStreakDayNumbers[dayKey] ?: 1
                basePay = standard * rate * sickPayPercentage(streakNumber)
                tier1Pay = 0.0
                tier2Pay = 0.0
            } else {
                val rates = tiers(session.dayType)
                basePay = base * rate * rates.base
                tier1Pay = tier1 * rate * rates.tier1
                tier2Pay = tier2 * rate * rates.tier2
            }

            slices.add(
                Slice(
                    session = session,
                    base = base,
                    tier1 = tier1,
                    tier2 = tier2,
                    hours = hours,
                    gas = gas,
                    basePay = basePay,
                    tier1Pay = tier1Pay,
                    tier2Pay = tier2Pay,
                    gross = basePay + tier1Pay + tier2Pay + gas,
                ),
            )
        }

        // Deductions are estimated on the whole day's gross, then apportioned pro-rata.
        val dayGross = slices.fold(0.0) { sum, slice -> sum + slice.gross }
        val dayTax = IsraeliTaxEstimator.estimateDailyNet(dayGross, settings, calendar)
        val points = TaxCreditPointsCalculator.creditPoints(settings)

        return slices.map { slice ->
            val share = if (dayGross > 0) slice.gross / dayGross else 0.0
            SessionBreakdown(
                slice.session,
                DayPayBreakdown(
                    regularHours = slice.base,
                    ot125Hours = slice.tier1,
                    ot150Hours = slice.tier2,
                    totalHours = slice.hours,
                    gasAllowance = slice.gas,
                    basePay = slice.basePay,
                    ot125Pay = slice.tier1Pay,
                    ot150Pay = slice.tier2Pay,
                    totalPay = slice.gross,
                    netPay = dayTax.net * share,
                    incomeTax = dayTax.incomeTax * share,
                    nationalInsurance = dayTax.nationalInsurance * share,
                    healthTax = dayTax.healthTax * share,
                    creditPointsApplied = dayTax.creditApplied * share,
                    creditPoints = points,
                    currencyCode = settings.currencyCode,
                ),
            )
        }
    }

    /** Day-aware breakdowns for any set of sessions, grouped by calendar day (oldest day first). */
    fun dayAwareBreakdowns(
        sessions: List<WorkSession>,
        settings: WorkplaceSettings,
        calendar: IosCalendar,
    ): List<SessionBreakdown> {
        val groups = LinkedHashMap<Instant, MutableList<WorkSession>>()
        for (session in sessions) {
            groups.getOrPut(calendar.startOfDay(session.date)) { ArrayList() }.add(session)
        }
        val streaks = sickStreakDayNumbers(sessions, calendar)
        return groups.entries
            .sortedBy { it.key }
            .flatMap { breakdowns(it.value, settings, calendar, streaks) }
    }

    /** Breakdown of a single session evaluated alone (no same-day context). */
    fun breakdown(session: WorkSession, settings: WorkplaceSettings, calendar: IosCalendar): DayPayBreakdown =
        breakdowns(listOf(session), settings, calendar)[0].breakdown

    /** Breakdown of a single session in the context of all its same-day sessions. */
    fun breakdown(
        session: WorkSession,
        allSessions: List<WorkSession>,
        settings: WorkplaceSettings,
        calendar: IosCalendar,
    ): DayPayBreakdown {
        val sameDay = allSessions.filter { calendar.isSameDay(it.date, session.date) && !it.isOpen }.toMutableList()
        if (sameDay.none { it.id == session.id }) {
            sameDay.add(session)
        }
        val streaks = sickStreakDayNumbers(allSessions, calendar)
        val results = breakdowns(sameDay, settings, calendar, streaks)
        return results.firstOrNull { it.session.id == session.id }?.breakdown
            ?: breakdown(session, settings, calendar)
    }

    /** Round monetary values to 2 decimal places, halves away from zero (Swift `.rounded()`). */
    private fun round(value: Double): Double = roundedAwayFromZero(value * 100) / 100

    /**
     * Period totals. [calendar] is used only for the weekly grouping, as on iOS; the day grouping
     * and sick streaks use [currentCalendar] (`Calendar.current` on iOS).
     *
     * The weekly excess is summed in order of each week's first appearance. Swift sums it in
     * `Dictionary` order, which is random per process; the last bits only agree when the
     * weekly totals add exactly (multiples of 0.25 h).
     */
    fun aggregate(
        sessions: List<WorkSession>,
        settings: WorkplaceSettings,
        calendar: IosCalendar,
        currentCalendar: IosCalendar = calendar,
    ): DayPayBreakdown {
        val completed = sessions.filter { it.clockOut != null }
        val totals = dayAwareBreakdowns(completed, settings, currentCalendar).map { it.breakdown }
        var regular = totals.fold(0.0) { sum, item -> sum + item.regularHours }
        var ot125 = totals.fold(0.0) { sum, item -> sum + item.ot125Hours }
        var ot150 = totals.fold(0.0) { sum, item -> sum + item.ot150Hours }
        val hours = totals.fold(0.0) { sum, item -> sum + item.totalHours }
        val gas = totals.fold(0.0) { sum, item -> sum + item.gasAllowance }
        var basePay = totals.fold(0.0) { sum, item -> sum + item.basePay }
        var ot125Pay = totals.fold(0.0) { sum, item -> sum + item.ot125Pay }
        var ot150Pay = totals.fold(0.0) { sum, item -> sum + item.ot150Pay }
        val net = totals.fold(0.0) { sum, item -> sum + item.netPay }
        val incomeTax = totals.fold(0.0) { sum, item -> sum + item.incomeTax }
        val ni = totals.fold(0.0) { sum, item -> sum + item.nationalInsurance }
        val health = totals.fold(0.0) { sum, item -> sum + item.healthTax }
        val credit = totals.fold(0.0) { sum, item -> sum + item.creditPointsApplied }
        val points = TaxCreditPointsCalculator.creditPoints(settings)

        val rate = settings.hourlyRate
        val dailyOT = ot125 + ot150

        val groupedByWeek = LinkedHashMap<Pair<Int, Int>, MutableList<WorkSession>>()
        for (session in completed) {
            groupedByWeek.getOrPut(calendar.yearAndWeekOfYear(session.clockIn)) { ArrayList() }.add(session)
        }
        var weeklyExcessHours = 0.0
        for (weekSessions in groupedByWeek.values) {
            val weekTotal = weekSessions.fold(0.0) { sum, item -> sum + item.effectiveHours }
            weeklyExcessHours += swiftMax(0.0, weekTotal - settings.weeklyStandardHours)
        }

        if (weeklyExcessHours > dailyOT) {
            val needsWeeklyOT = weeklyExcessHours - dailyOT
            val weeklyOT125 = swiftMin(needsWeeklyOT, settings.weeklyOvertimeCapHours)
            val weeklyOT150 = swiftMax(0.0, needsWeeklyOT - settings.weeklyOvertimeCapHours)
            val promoted125 = swiftMin(regular, weeklyOT125)
            val promoted150 = swiftMin(regular - promoted125, weeklyOT150)
            regular -= (promoted125 + promoted150)
            ot125 += promoted125
            ot150 += promoted150
            basePay = round(basePay - (promoted125 + promoted150) * rate)
            ot125Pay = round(ot125Pay + promoted125 * rate * 1.25)
            ot150Pay = round(ot150Pay + promoted150 * rate * 1.5)
        }

        val adjustedGross = basePay + ot125Pay + ot150Pay + gas

        return DayPayBreakdown(
            regularHours = regular,
            ot125Hours = ot125,
            ot150Hours = ot150,
            totalHours = hours,
            gasAllowance = round(gas),
            basePay = basePay,
            ot125Pay = ot125Pay,
            ot150Pay = ot150Pay,
            totalPay = round(adjustedGross),
            netPay = round(net),
            incomeTax = round(incomeTax),
            nationalInsurance = round(ni),
            healthTax = round(health),
            creditPointsApplied = round(credit),
            creditPoints = points,
            currencyCode = settings.currencyCode,
        )
    }
}
