package com.hourstracker.model.golden

import com.hourstracker.model.BreakInterval
import com.hourstracker.model.DayPayBreakdown
import com.hourstracker.model.DayType
import com.hourstracker.model.IosCalendar
import com.hourstracker.model.MaritalStatus
import com.hourstracker.model.WorkSession
import com.hourstracker.model.WorkplaceSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** The environment a golden file was produced in, as recorded in the file itself. */
class GoldenEnv(json: JsonObject) {
    val id: String = json.str("id")
    val timeZone: String = json.str("timeZone")
    val firstWeekday: Int = json["firstWeekday"]!!.jsonPrimitive.int
    val minimumDaysInFirstWeek: Int = json["minimumDaysInFirstWeek"]!!.jsonPrimitive.int
    val systemLocale: String = json.str("systemLocale")
    val platform: String = json.str("platform")

    val zone: ZoneId = ZoneId.of(timeZone)

    /** The calendar iOS used: pinned zone, first weekday and minimal days, and the real clock. */
    val calendar: IosCalendar = IosCalendar(zone, firstWeekday, minimumDaysInFirstWeek, Clock.system(zone))
}

class GoldenFile(val name: String, val group: String, val env: GoldenEnv, val cases: List<JsonObject>)

object Golden {
    val dir: File = File(requireNotNull(System.getProperty("golden.dir")) { "golden.dir is not set" })

    private val json = Json

    fun read(name: String): JsonObject = json.parseToJsonElement(File(dir, name).readText()).jsonObject

    /** Every `<group>.<env>.json` file, in environment order. */
    fun files(group: String): List<GoldenFile> {
        val names = dir.list().orEmpty().filter { it.startsWith("$group.") && it.endsWith(".json") }.sorted()
        require(names.isNotEmpty()) { "no golden files for group $group in $dir" }
        return names.map { name ->
            val root = read(name)
            GoldenFile(
                name = name,
                group = root.str("group"),
                env = GoldenEnv(root["environment"]!!.jsonObject),
                cases = root["cases"]!!.jsonArray.map { it.jsonObject },
            )
        }
    }
}

/** One golden case being checked, with helpers that name the file, case and field on a mismatch. */
class Check(val file: GoldenFile, val case: JsonObject) {
    val env: GoldenEnv get() = file.env
    val cal: IosCalendar get() = file.env.calendar
    val id: String = case.str("id")

    fun where(field: String) = "${file.name} :: $id :: $field"

    /** Bit-for-bit comparison of a Double. */
    fun bits(field: String, expected: JsonElement, actual: Double) {
        val obj = expected.jsonObject
        val expectedBits = obj.str("bits")
        val actualBits = String.format("%016x", java.lang.Double.doubleToRawLongBits(actual))
        if (expectedBits != actualBits) {
            throw AssertionError(
                "${where(field)}: expected bits $expectedBits (${obj["value"]}) but was $actualBits ($actual)",
            )
        }
    }

    fun <T> same(field: String, expected: T, actual: T) = assertEquals(expected, actual, where(field))

    fun breakdown(field: String, expected: JsonObject, actual: DayPayBreakdown) {
        bits("$field.regularHours", expected["regularHours"]!!, actual.regularHours)
        bits("$field.ot125Hours", expected["ot125Hours"]!!, actual.ot125Hours)
        bits("$field.ot150Hours", expected["ot150Hours"]!!, actual.ot150Hours)
        bits("$field.totalHours", expected["totalHours"]!!, actual.totalHours)
        bits("$field.gasAllowance", expected["gasAllowance"]!!, actual.gasAllowance)
        bits("$field.basePay", expected["basePay"]!!, actual.basePay)
        bits("$field.ot125Pay", expected["ot125Pay"]!!, actual.ot125Pay)
        bits("$field.ot150Pay", expected["ot150Pay"]!!, actual.ot150Pay)
        bits("$field.totalPay", expected["totalPay"]!!, actual.totalPay)
        bits("$field.netPay", expected["netPay"]!!, actual.netPay)
        bits("$field.incomeTax", expected["incomeTax"]!!, actual.incomeTax)
        bits("$field.nationalInsurance", expected["nationalInsurance"]!!, actual.nationalInsurance)
        bits("$field.healthTax", expected["healthTax"]!!, actual.healthTax)
        bits("$field.creditPointsApplied", expected["creditPointsApplied"]!!, actual.creditPointsApplied)
        bits("$field.creditPoints", expected["creditPoints"]!!, actual.creditPoints)
        same("$field.currencyCode", expected.str("currencyCode"), actual.currencyCode)
    }

    fun settings(json: JsonObject): WorkplaceSettings = settingsFromSpec(json, cal)

    fun sessions(array: JsonArray): List<WorkSession> = array.map { sessionFromSpec(it.jsonObject) }

    /** "yyyy-MM-dd" of an instant in the file's zone. */
    fun localDay(instant: Instant): String = cal.localDate(instant).toString()

    /** 12:00 local on a "yyyy-MM-dd" day (how the harness builds anchors). */
    fun noon(day: String): Instant = LocalDate.parse(day).atTime(12, 0).atZone(env.zone).toInstant()
}

/** Runs [check] for every case of every file of [group], one JUnit test per case. */
fun goldenTests(group: String, check: (Check) -> Unit): List<DynamicTest> =
    Golden.files(group).flatMap { file ->
        file.cases.map { case ->
            val id = case.str("id")
            DynamicTest.dynamicTest("${file.name} :: $id") { check(Check(file, case)) }
        }
    }

internal fun JsonObject.str(key: String): String = this[key]!!.jsonPrimitive.content

internal fun JsonObject.dbl(key: String): Double = this[key]!!.jsonPrimitive.double

internal fun JsonObject.optStr(key: String): String? = this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content

internal fun JsonObject.optInt(key: String): Int? = this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.int

internal fun JsonObject.bool(key: String): Boolean = this[key]!!.jsonPrimitive.boolean

/** The harness's `SettingsSpec` (absent keys are the defaults the harness uses). */
fun settingsFromSpec(json: JsonObject, calendar: IosCalendar): WorkplaceSettings = WorkplaceSettings(
    hourlyRate = json["hourlyRate"]?.jsonPrimitive?.double ?: 100.0,
    dailyGasAllowance = json["dailyGasAllowance"]?.jsonPrimitive?.double ?: 35.0,
    standardDayHours = json["standardDayHours"]?.jsonPrimitive?.double ?: 8.6,
    ot125HoursCap = json["ot125HoursCap"]?.jsonPrimitive?.double ?: 2.0,
    locationRadiusMeters = 150.0,
    maritalStatus = MaritalStatus.fromRaw(json["maritalStatus"]?.jsonPrimitive?.content ?: "single")!!,
    hasChildren = json["hasChildren"]?.jsonPrimitive?.boolean ?: false,
    numberOfChildren = json["numberOfChildren"]?.jsonPrimitive?.int ?: 0,
    spouseEmployed = json["spouseEmployed"]?.jsonPrimitive?.boolean ?: false,
    birthDate = json.optStr("birthDate")?.let { LocalDate.parse(it).let { d -> calendar.dateFrom(d.year, d.monthValue, d.dayOfMonth) } },
    payrollStartDay = json["payrollStartDay"]?.jsonPrimitive?.int ?: 1,
    restDayWeekday = json["restDayWeekday"]?.jsonPrimitive?.int ?: 7,
    secondRestDayWeekday = json.optInt("secondRestDayWeekday"),
    defaultBreakMinutes = json["defaultBreakMinutes"]?.jsonPrimitive?.int ?: 0,
    breaksArePaid = json["breaksArePaid"]?.jsonPrimitive?.boolean ?: false,
    nightStandardDayHours = json["nightStandardDayHours"]?.jsonPrimitive?.double ?: 7.0,
    weeklyStandardHours = json["weeklyStandardHours"]?.jsonPrimitive?.double ?: 42.0,
    weeklyOvertimeCapHours = json["weeklyOvertimeCapHours"]?.jsonPrimitive?.double ?: 12.0,
    currencyCode = json["currencyCode"]?.jsonPrimitive?.content ?: "ILS",
    expectedShiftStartHour = json["expectedShiftStartHour"]?.jsonPrimitive?.int ?: 8,
    expectedShiftStartMinute = json["expectedShiftStartMinute"]?.jsonPrimitive?.int ?: 0,
)

/** The harness's `SessionSpec`. */
fun sessionFromSpec(json: JsonObject): WorkSession = WorkSession(
    id = UUID.fromString(json.str("id")),
    date = Instant.parse(json.str("date")),
    clockIn = Instant.parse(json.str("clockIn")),
    clockOut = json.optStr("clockOut")?.let { Instant.parse(it) },
    breakMinutes = json["breakMinutes"]?.jsonPrimitive?.int ?: 0,
    breaks = json["breaks"]?.jsonArray.orEmpty().map { breakFromSpec(it.jsonObject) },
    dayType = DayType.fromRaw(json["dayType"]?.jsonPrimitive?.content ?: "regular")!!,
    isNightShift = json["isNightShift"]?.jsonPrimitive?.boolean ?: false,
)

fun breakFromSpec(json: JsonObject): BreakInterval =
    BreakInterval(Instant.parse(json.str("start")), json.optStr("end")?.let { Instant.parse(it) })

internal fun JsonPrimitive.isIntegral(): Boolean = content.toLongOrNull() != null
