package com.hourstracker.model.golden

import com.hourstracker.model.IsraeliTaxEstimator
import com.hourstracker.model.MaritalStatus
import com.hourstracker.model.TaxCreditPointsCalculator
import com.hourstracker.model.WorkplaceSettings
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/** Groups D and J: tax estimate, credit points, and the clamping done when settings are built or decoded. */
class TaxAndSettingsGoldenTest {
    @TestFactory
    fun `monthly deductions`(): List<DynamicTest> = goldenTests("tax_monthly") { c ->
        val input = c.case["input"]!!.jsonObject
        val settings = c.settings(input["settings"]!!.jsonObject)
        val result = IsraeliTaxEstimator.estimateMonthlyDeductions(input.dbl("monthlyGross"), settings, c.cal)
        val out = c.case["output"]!!.jsonObject
        c.bits("incomeTax", out["incomeTax"]!!, result.incomeTax)
        c.bits("nationalInsurance", out["nationalInsurance"]!!, result.nationalInsurance)
        c.bits("healthTax", out["healthTax"]!!, result.healthTax)
        c.bits("creditOffset", out["creditOffset"]!!, result.creditOffset)
        c.bits("total", out["total"]!!, result.total)
        c.same("retired", out.bool("retired"), settings.hasReachedRetirementAge(c.cal))
    }

    @TestFactory
    fun `daily net`(): List<DynamicTest> = goldenTests("tax_daily") { c ->
        val input = c.case["input"]!!.jsonObject
        val settings = c.settings(input["settings"]!!.jsonObject)
        val result = IsraeliTaxEstimator.estimateDailyNet(input.dbl("dailyGross"), settings, c.cal)
        val out = c.case["output"]!!.jsonObject
        c.bits("net", out["net"]!!, result.net)
        c.bits("incomeTax", out["incomeTax"]!!, result.incomeTax)
        c.bits("nationalInsurance", out["nationalInsurance"]!!, result.nationalInsurance)
        c.bits("healthTax", out["healthTax"]!!, result.healthTax)
        c.bits("creditApplied", out["creditApplied"]!!, result.creditApplied)
    }

    @TestFactory
    fun `credit points`(): List<DynamicTest> = goldenTests("tax_credit") { c ->
        val settings = c.settings(c.case["input"]!!.jsonObject)
        val out = c.case["output"]!!.jsonObject
        c.same("numberOfChildren", out.optInt("numberOfChildren"), settings.numberOfChildren)
        c.bits("creditPoints", out["creditPoints"]!!, TaxCreditPointsCalculator.creditPoints(settings))
        c.bits("monthlyCreditValue", out["monthlyCreditValue"]!!, TaxCreditPointsCalculator.monthlyCreditValue(settings))
        c.bits("dailyCreditValue", out["dailyCreditValue"]!!, TaxCreditPointsCalculator.dailyCreditValue(settings))
    }

    @TestFactory
    fun `settings normalization`(): List<DynamicTest> = goldenTests("settings_normalization") { c ->
        val case = c.case
        val decodedExpected = case.bool("decoded")
        val settings: WorkplaceSettings? = if (case["spec"] != null) {
            c.settings(case["spec"]!!.jsonObject)
        } else {
            decodeSettingsJson(case.str("json"))
        }
        c.same("decoded", decodedExpected, settings != null)
        if (!decodedExpected) return@goldenTests

        val expected = case["settings"]!!.jsonObject
        val actual = settings!!
        c.bits("hourlyRate", expected["hourlyRate"]!!, actual.hourlyRate)
        c.bits("dailyGasAllowance", expected["dailyGasAllowance"]!!, actual.dailyGasAllowance)
        c.bits("standardDayHours", expected["standardDayHours"]!!, actual.standardDayHours)
        c.bits("ot125HoursCap", expected["ot125HoursCap"]!!, actual.ot125HoursCap)
        c.bits("nightStandardDayHours", expected["nightStandardDayHours"]!!, actual.nightStandardDayHours)
        c.bits("weeklyStandardHours", expected["weeklyStandardHours"]!!, actual.weeklyStandardHours)
        c.bits("weeklyOvertimeCapHours", expected["weeklyOvertimeCapHours"]!!, actual.weeklyOvertimeCapHours)
        c.bits("locationRadiusMeters", expected["locationRadiusMeters"]!!, actual.locationRadiusMeters)
        c.same("numberOfChildren", expected.optInt("numberOfChildren"), actual.numberOfChildren)
        c.same("payrollStartDay", expected.optInt("payrollStartDay"), actual.payrollStartDay)
        c.same("restDayWeekday", expected.optInt("restDayWeekday"), actual.restDayWeekday)
        c.same("secondRestDayWeekday", expected.optInt("secondRestDayWeekday"), actual.secondRestDayWeekday)
        c.same("defaultBreakMinutes", expected.optInt("defaultBreakMinutes"), actual.defaultBreakMinutes)
        c.same("expectedShiftStartHour", expected.optInt("expectedShiftStartHour"), actual.expectedShiftStartHour)
        c.same("expectedShiftStartMinute", expected.optInt("expectedShiftStartMinute"), actual.expectedShiftStartMinute)
        c.same("maritalStatus", expected.str("maritalStatus"), actual.maritalStatus.raw)
        c.same("hasChildren", expected.bool("hasChildren"), actual.hasChildren)
        c.same("spouseEmployed", expected.bool("spouseEmployed"), actual.spouseEmployed)
        c.same("breaksArePaid", expected.bool("breaksArePaid"), actual.breaksArePaid)
        c.same("currencyCode", expected.str("currencyCode"), actual.currencyCode)
    }
}

/**
 * Decodes settings JSON the way the iOS `Decodable` does for the keys that matter to the pay engine:
 * some keys are required, absent or null optional keys take their default, a value of the wrong type
 * or an unknown marital status makes decoding fail, and the clamping runs afterwards. Returns null on failure.
 */
internal fun decodeSettingsJson(text: String): WorkplaceSettings? = try {
    val json = Json.parseToJsonElement(text).jsonObject

    fun required(key: String): JsonPrimitive = json[key]?.takeUnless { it is JsonNull }?.jsonPrimitive ?: error("missing $key")
    fun optional(key: String): JsonPrimitive? = json[key]?.takeUnless { it is JsonNull }?.jsonPrimitive

    fun double(p: JsonPrimitive): Double = if (p.isString) error("not a number") else p.double
    fun int(p: JsonPrimitive): Int = if (p.isString || !p.isIntegral()) error("not an integer") else p.int
    fun bool(p: JsonPrimitive): Boolean = if (p.isString) error("not a boolean") else p.boolean
    fun string(p: JsonPrimitive): String = if (p.isString) p.content else error("not a string")

    string(required("workplaceName"))
    string(required("workerFullName"))
    string(required("workerIDNumber"))
    string(required("employeeNumber"))

    WorkplaceSettings(
        hourlyRate = double(required("hourlyRate")),
        dailyGasAllowance = double(required("dailyGasAllowance")),
        standardDayHours = double(required("standardDayHours")),
        ot125HoursCap = double(required("ot125HoursCap")),
        locationRadiusMeters = double(required("locationRadiusMeters")),
        maritalStatus = optional("maritalStatus")?.let { MaritalStatus.fromRaw(string(it)) ?: error("unknown marital status") }
            ?: MaritalStatus.Single,
        hasChildren = optional("hasChildren")?.let(::bool) ?: false,
        numberOfChildren = optional("numberOfChildren")?.let(::int) ?: 0,
        spouseEmployed = optional("spouseEmployed")?.let(::bool) ?: false,
        payrollStartDay = optional("payrollStartDay")?.let(::int) ?: 1,
        restDayWeekday = optional("restDayWeekday")?.let(::int) ?: 7,
        secondRestDayWeekday = optional("secondRestDayWeekday")?.let(::int),
        defaultBreakMinutes = optional("defaultBreakMinutes")?.let(::int) ?: 0,
        breaksArePaid = optional("breaksArePaid")?.let(::bool) ?: false,
        nightStandardDayHours = optional("nightStandardDayHours")?.let(::double) ?: 7.0,
        weeklyStandardHours = optional("weeklyStandardHours")?.let(::double) ?: 42.0,
        weeklyOvertimeCapHours = optional("weeklyOvertimeCapHours")?.let(::double) ?: 12.0,
        currencyCode = optional("currencyCode")?.let(::string) ?: "ILS",
        expectedShiftStartHour = optional("expectedShiftStartHour")?.let(::int) ?: 8,
        expectedShiftStartMinute = optional("expectedShiftStartMinute")?.let(::int) ?: 0,
    )
} catch (_: IllegalStateException) {
    null
} catch (_: IllegalArgumentException) {
    null
} catch (_: kotlinx.serialization.SerializationException) {
    null
}
