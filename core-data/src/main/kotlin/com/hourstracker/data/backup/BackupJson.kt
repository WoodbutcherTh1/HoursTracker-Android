package com.hourstracker.data.backup

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Reads and writes the iOS backup JSON. The rules follow Swift's `Codable` for these types: dates are ISO-8601 text
 * in UTC with whole seconds, some keys are required, absent or null optional keys take their default, and a value of
 * the wrong type is an error. Unknown keys are ignored, so a full export (which has extra fields) is accepted too.
 */
object BackupJson {
    fun parse(text: String): BackupPayload = try {
        val root = JSONObject(text)
        BackupPayload(
            settings = parseSettings(root.getJSONObject("settings")),
            sessions = root.getJSONArray("sessions").objects().map(::parseSession),
        )
    } catch (e: JSONException) {
        throw BackupFormatException("The backup is not valid: ${e.message}", e)
    } catch (e: IllegalArgumentException) {
        throw BackupFormatException("The backup is not valid: ${e.message}", e)
    } catch (e: java.time.format.DateTimeParseException) {
        throw BackupFormatException("The backup has a date that cannot be read: ${e.parsedString}", e)
    }

    fun write(payload: BackupPayload): String = JSONObject()
        .put("settings", writeSettings(payload.settings))
        .put("sessions", JSONArray(payload.sessions.map(::writeSession)))
        .toString()

    // region reading

    private fun parseSession(o: JSONObject) = BackupSession(
        id = UUID.fromString(o.str("id")),
        date = o.instant("date"),
        clockIn = o.instant("clockIn"),
        clockOut = o.optInstant("clockOut"),
        isManualEntry = o.bool("isManualEntry"),
        isAIImported = o.optBool("isAIImported", false),
        breakMinutes = maxOf(0, o.optInteger("breakMinutes", 0)),
        breaks = o.optArray("breaks").objects().map { BackupBreak(it.instant("start"), it.optInstant("end")) },
        dayType = (o.optText("dayType") ?: "regular").also { require(it in DAY_TYPES) { "unknown dayType $it" } },
        isNightShift = o.optBool("isNightShift", false),
        notes = o.optText("notes"),
        modifiedAt = o.optInstant("modifiedAt") ?: Instant.EPOCH,
        workplaceId = o.optText("workplaceID")?.let { runCatching { UUID.fromString(it) }.getOrNull() },
    )

    private fun parseSettings(o: JSONObject): BackupSettings = BackupSettings(
        workplaceName = o.str("workplaceName"),
        contractorName = o.optText("contractorName"),
        workerFullName = o.str("workerFullName"),
        workerIdNumber = o.str("workerIDNumber"),
        employeeNumber = o.str("employeeNumber"),
        hourlyRate = o.num("hourlyRate"),
        dailyGasAllowance = o.num("dailyGasAllowance"),
        standardDayHours = o.num("standardDayHours"),
        ot125HoursCap = o.num("ot125HoursCap"),
        locationLatitude = o.optNumber("locationLatitude"),
        locationLongitude = o.optNumber("locationLongitude"),
        locationRadiusMeters = o.num("locationRadiusMeters"),
        maritalStatus = (o.optText("maritalStatus") ?: "single").also { require(it in MARITAL_STATUSES) { "unknown maritalStatus $it" } },
        hasChildren = o.optBool("hasChildren", false),
        numberOfChildren = o.optInteger("numberOfChildren", 0),
        spouseEmployed = o.optBool("spouseEmployed", false),
        birthDate = o.optInstant("birthDate"),
        payrollStartDay = o.optInteger("payrollStartDay", 1),
        restDayWeekday = o.optInteger("restDayWeekday", 7),
        secondRestDayWeekday = o.optIntegerOrNull("secondRestDayWeekday"),
        defaultBreakMinutes = o.optInteger("defaultBreakMinutes", 0),
        breaksArePaid = o.optBool("breaksArePaid", false),
        nightStandardDayHours = o.optNumber("nightStandardDayHours") ?: 7.0,
        weeklyStandardHours = o.optNumber("weeklyStandardHours") ?: 42.0,
        weeklyOvertimeCapHours = o.optNumber("weeklyOvertimeCapHours") ?: 12.0,
        currencyCode = (o.optText("currencyCode") ?: "ILS"),
        arrivalRemindersEnabled = o.optBool("arrivalRemindersEnabled", false),
        expectedShiftStartHour = o.optInteger("expectedShiftStartHour", 8),
        expectedShiftStartMinute = o.optInteger("expectedShiftStartMinute", 0),
        // A malformed leave day or other job must never make the whole backup unreadable.
        leaveDays = runCatching { o.optArray("leaveDays").objects().map(::parseLeaveDay) }.getOrDefault(emptyList()),
        additionalWorkplaces = runCatching { o.optArray("additionalWorkplaces").objects().map(::parseWorkplace) }.getOrDefault(emptyList()),
        modifiedAt = o.optInstant("modifiedAt") ?: Instant.EPOCH,
    )

    private fun parseLeaveDay(o: JSONObject) = BackupLeaveDay(
        id = UUID.fromString(o.str("id")),
        date = o.instant("date"),
        kind = o.str("kind").also { require(it in LEAVE_KINDS) { "unknown leave kind $it" } },
    )

    private fun parseWorkplace(o: JSONObject) = BackupWorkplace(
        id = UUID.fromString(o.str("id")),
        colorIndex = o.optIntegerOrNull("colorIndex") ?: error("colorIndex is missing"),
        settings = parseSettings(o.getJSONObject("settings")),
    )

    // endregion

    // region writing

    private fun writeSession(s: BackupSession): JSONObject = JSONObject().apply {
        put("id", s.id.toString().uppercase())
        put("date", iso(s.date))
        put("clockIn", iso(s.clockIn))
        s.clockOut?.let { put("clockOut", iso(it)) }
        put("isManualEntry", s.isManualEntry)
        put("isAIImported", s.isAIImported)
        put("breakMinutes", s.breakMinutes)
        put("breaks", JSONArray(s.breaks.map { b -> JSONObject().put("start", iso(b.start)).also { o -> b.end?.let { e -> o.put("end", iso(e)) } } }))
        put("dayType", s.dayType)
        put("isNightShift", s.isNightShift)
        s.notes?.let { put("notes", it) }
        put("modifiedAt", iso(s.modifiedAt))
        s.workplaceId?.let { put("workplaceID", it.toString().uppercase()) }
    }

    private fun writeSettings(s: BackupSettings): JSONObject = JSONObject().apply {
        put("workplaceName", s.workplaceName)
        s.contractorName?.let { put("contractorName", it) }
        put("workerFullName", s.workerFullName)
        put("workerIDNumber", s.workerIdNumber)
        put("employeeNumber", s.employeeNumber)
        put("hourlyRate", s.hourlyRate)
        put("dailyGasAllowance", s.dailyGasAllowance)
        put("standardDayHours", s.standardDayHours)
        put("ot125HoursCap", s.ot125HoursCap)
        s.locationLatitude?.let { put("locationLatitude", it) }
        s.locationLongitude?.let { put("locationLongitude", it) }
        put("locationRadiusMeters", s.locationRadiusMeters)
        put("maritalStatus", s.maritalStatus)
        put("hasChildren", s.hasChildren)
        put("numberOfChildren", s.numberOfChildren)
        put("spouseEmployed", s.spouseEmployed)
        s.birthDate?.let { put("birthDate", iso(it)) }
        put("payrollStartDay", s.payrollStartDay)
        put("restDayWeekday", s.restDayWeekday)
        s.secondRestDayWeekday?.let { put("secondRestDayWeekday", it) }
        put("defaultBreakMinutes", s.defaultBreakMinutes)
        put("breaksArePaid", s.breaksArePaid)
        put("nightStandardDayHours", s.nightStandardDayHours)
        put("weeklyStandardHours", s.weeklyStandardHours)
        put("weeklyOvertimeCapHours", s.weeklyOvertimeCapHours)
        put("currencyCode", s.currencyCode)
        put("arrivalRemindersEnabled", s.arrivalRemindersEnabled)
        put("expectedShiftStartHour", s.expectedShiftStartHour)
        put("expectedShiftStartMinute", s.expectedShiftStartMinute)
        put("leaveDays", JSONArray(s.leaveDays.map { l -> JSONObject().put("id", l.id.toString().uppercase()).put("date", iso(l.date)).put("kind", l.kind) }))
        put(
            "additionalWorkplaces",
            JSONArray(s.additionalWorkplaces.map { w -> JSONObject().put("id", w.id.toString().uppercase()).put("colorIndex", w.colorIndex).put("settings", writeSettings(w.settings)) }),
        )
        put("modifiedAt", iso(s.modifiedAt))
    }

    // endregion

    /** ISO-8601 in UTC with whole seconds, like Swift's `.iso8601` strategy. */
    private fun iso(instant: Instant): String = DateTimeFormatter.ISO_INSTANT.format(instant.truncatedTo(ChronoUnit.SECONDS))

    private val DAY_TYPES = setOf("regular", "restDay", "holiday", "sick")
    private val MARITAL_STATUSES = setOf("single", "married")
    private val LEAVE_KINDS = setOf("vacation", "recuperation")

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun JSONObject.optArray(key: String): JSONArray = if (isNull(key)) JSONArray() else getJSONArray(key)

    // org.json converts between types ("55" reads as a number); Swift's decoder does not, so these check the type.
    private fun JSONObject.str(key: String): String {
        val value = get(key)
        require(value is String) { "$key is not text" }
        return value
    }

    private fun JSONObject.num(key: String): Double {
        val value = get(key)
        require(value is Number) { "$key is not a number" }
        return value.toDouble()
    }

    private fun JSONObject.bool(key: String): Boolean {
        val value = get(key)
        require(value is Boolean) { "$key is not true or false" }
        return value
    }

    private fun JSONObject.instant(key: String): Instant = Instant.parse(str(key))

    private fun JSONObject.optInstant(key: String): Instant? = if (isNull(key)) null else instant(key)

    private fun JSONObject.optText(key: String): String? = if (isNull(key)) null else str(key)

    private fun JSONObject.optBool(key: String, default: Boolean): Boolean = if (isNull(key)) default else bool(key)

    private fun JSONObject.optNumber(key: String): Double? = if (isNull(key)) null else num(key)

    /** An integer: 3 is accepted, 3.5 and "3" are not, as with Swift's `Int`. */
    private fun JSONObject.optIntegerOrNull(key: String): Int? {
        if (isNull(key)) return null
        val value = get(key)
        require(value is Int || value is Long) { "$key is not an integer" }
        return (value as Number).toInt()
    }

    private fun JSONObject.optInteger(key: String, default: Int): Int = optIntegerOrNull(key) ?: default
}
