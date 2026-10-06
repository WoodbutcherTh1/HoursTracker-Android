package com.hourstracker.model

/** Israeli tax credit points (simplified baseline rules). */
object TaxCreditPointsCalculator {
    const val BASE_RESIDENT_POINTS: Double = 2.25
    const val CREDIT_POINT_MONTHLY_VALUE_ILS: Double = 242.0
    const val AVERAGE_WORKING_DAYS_PER_MONTH: Double = 21.67

    fun creditPoints(settings: WorkplaceSettings): Double {
        var points = BASE_RESIDENT_POINTS
        if (settings.hasChildren) {
            points += maxOf(0, settings.numberOfChildren).toDouble()
        }
        if (settings.maritalStatus == MaritalStatus.Married && !settings.spouseEmployed) {
            points += 0.5
        }
        return points
    }

    fun monthlyCreditValue(settings: WorkplaceSettings): Double =
        creditPoints(settings) * CREDIT_POINT_MONTHLY_VALUE_ILS

    fun dailyCreditValue(settings: WorkplaceSettings): Double =
        monthlyCreditValue(settings) / AVERAGE_WORKING_DAYS_PER_MONTH
}

data class MonthlyDeductions(
    /** Income tax after the credit-point offset has been applied. */
    val incomeTax: Double,
    val nationalInsurance: Double,
    val healthTax: Double,
    /** How much credit was applied to income tax (reporting only). */
    val creditOffset: Double,
) {
    val total: Double get() = incomeTax + nationalInsurance + healthTax
}

data class DailyNet(
    val net: Double,
    val incomeTax: Double,
    val nationalInsurance: Double,
    val healthTax: Double,
    val creditApplied: Double,
)

/** Estimated Israeli income tax, National Insurance and Health Tax. Planning estimates only. */
object IsraeliTaxEstimator {
    private const val AVERAGE_WAGE_THRESHOLD_MONTHLY = 7_522.0

    private class Bracket(val limit: Double, val rate: Double)

    private val monthlyBrackets = listOf(
        Bracket(7_010.0, 0.10),
        Bracket(10_060.0, 0.14),
        Bracket(16_150.0, 0.20),
        Bracket(22_440.0, 0.31),
        Bracket(46_690.0, 0.35),
        Bracket(60_130.0, 0.47),
        Bracket(Double.MAX_VALUE, 0.50),
    )

    fun estimateMonthlyDeductions(
        monthlyGross: Double,
        settings: WorkplaceSettings,
        calendar: IosCalendar,
    ): MonthlyDeductions {
        val gross = swiftMax(0.0, monthlyGross)
        val rawIncomeTax = progressiveTax(gross)
        val credit = TaxCreditPointsCalculator.monthlyCreditValue(settings)
        val incomeTax = swiftMax(0.0, rawIncomeTax - credit)
        val ni = nationalInsurance(gross, settings.hasReachedRetirementAge(calendar))
        val health = healthTax(gross)

        return MonthlyDeductions(
            incomeTax = incomeTax,
            nationalInsurance = ni,
            healthTax = health,
            creditOffset = swiftMin(credit, rawIncomeTax),
        )
    }

    /** Scale a single day's gross into an estimated monthly profile, then back to daily net. */
    fun estimateDailyNet(dailyGross: Double, settings: WorkplaceSettings, calendar: IosCalendar): DailyNet {
        val days = TaxCreditPointsCalculator.AVERAGE_WORKING_DAYS_PER_MONTH
        val monthlyGross = dailyGross * days
        val monthly = estimateMonthlyDeductions(monthlyGross, settings, calendar)

        return DailyNet(
            net = swiftMax(0.0, dailyGross - monthly.total / days),
            incomeTax = monthly.incomeTax / days,
            nationalInsurance = monthly.nationalInsurance / days,
            healthTax = monthly.healthTax / days,
            creditApplied = monthly.creditOffset / days,
        )
    }

    private fun progressiveTax(income: Double): Double {
        var remaining = income
        var previousLimit = 0.0
        var tax = 0.0

        for (bracket in monthlyBrackets) {
            val slice = swiftMin(remaining, bracket.limit - previousLimit)
            if (!(slice > 0)) break
            tax += slice * bracket.rate
            remaining -= slice
            previousLimit = bracket.limit
            if (remaining <= 0) break
        }
        return tax
    }

    private const val SENIOR_REDUCED_RATE = 0.0004
    private const val SENIOR_FULL_RATE = 0.0087

    private fun nationalInsurance(monthlyGross: Double, hasReachedRetirementAge: Boolean): Double {
        val reducedRate = if (hasReachedRetirementAge) SENIOR_REDUCED_RATE else 0.004
        val fullRate = if (hasReachedRetirementAge) SENIOR_FULL_RATE else 0.07
        val reduced = swiftMin(monthlyGross, AVERAGE_WAGE_THRESHOLD_MONTHLY) * reducedRate
        val full = swiftMax(0.0, monthlyGross - AVERAGE_WAGE_THRESHOLD_MONTHLY) * fullRate
        return reduced + full
    }

    private fun healthTax(monthlyGross: Double): Double {
        val reduced = swiftMin(monthlyGross, AVERAGE_WAGE_THRESHOLD_MONTHLY) * 0.031
        val full = swiftMax(0.0, monthlyGross - AVERAGE_WAGE_THRESHOLD_MONTHLY) * 0.05
        return reduced + full
    }
}
