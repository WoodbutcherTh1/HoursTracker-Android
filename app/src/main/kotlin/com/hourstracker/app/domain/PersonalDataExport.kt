package com.hourstracker.app.domain

import com.hourstracker.app.data.ConsentRecord
import com.hourstracker.app.data.UserProfile
import com.hourstracker.data.AuditAction
import com.hourstracker.data.ShiftRecord
import com.hourstracker.data.db.AuditLogEntity
import com.hourstracker.model.WorkplaceSettings
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/** The app choices that go into the personal data file (everything the person set that is not a pay setting). */
class AppChoices(
    val language: String,
    val theme: String,
    val showNet: Boolean,
    val breakRemindersEnabled: Boolean,
    val breakReminderMinutesBefore: Int,
    val shiftReminderEnabled: Boolean,
    val shiftReminderMinutesBefore: Int,
    val shiftSummaryEnabled: Boolean,
    val auditRetentionDays: Int,
    val shiftRetentionYears: Int,
    val statCardOrder: List<String>,
)

/**
 * "Download my data": everything this app holds about its owner, as one portable JSON document (GDPR Art. 15 and 20).
 *
 * The format is versioned ([SCHEMA_VERSION]); a reader should check it. Times are ISO-8601 in UTC. **The ID number is never
 * included** (it is protected separately and the owner knows it); `notIncluded` says so in the file itself.
 */
object PersonalDataExport {
    const val SCHEMA = "hourstracker.personal-data"
    const val SCHEMA_VERSION = 1

    fun build(
        exportedAt: Instant,
        appVersion: String,
        shifts: List<ShiftRecord>,
        settings: WorkplaceSettings,
        profile: UserProfile,
        choices: AppChoices,
        auditLog: List<AuditLogEntity>,
        consent: ConsentRecord?,
    ): String {
        val root = JSONObject()
        root.put("schema", SCHEMA)
        root.put("schemaVersion", SCHEMA_VERSION)
        root.put("exportedAt", exportedAt.toString())
        root.put("app", JSONObject().put("name", "HoursTracker").put("platform", "android").put("version", appVersion))
        root.put("notIncluded", JSONArray(listOf("idNumber")))
        root.put("note", "Your national ID number is not part of this file. Everything else this app stores about you is.")
        root.put(
            "profile",
            JSONObject().put("fullName", profile.fullName).put("employeeNumber", profile.employeeNumber)
                .put("workplaceName", profile.workplaceName).put("contractorName", profile.contractorName),
        )
        root.put("paySettings", paySettings(settings))
        root.put("appChoices", appChoices(choices))
        root.put("shifts", JSONArray(shifts.sortedBy { it.session.clockIn }.map(::shift)))
        root.put("auditLog", JSONArray(auditLog.sortedBy { it.timestamp }.map(::auditLine)))
        root.put("consents", consents(auditLog, consent))
        return root.toString(2)
    }

    private fun paySettings(s: WorkplaceSettings) = JSONObject()
        .put("hourlyRate", s.hourlyRate).put("dailyGasAllowance", s.dailyGasAllowance).put("standardDayHours", s.standardDayHours)
        .put("ot125HoursCap", s.ot125HoursCap).put("maritalStatus", s.maritalStatus.raw).put("hasChildren", s.hasChildren)
        .put("numberOfChildren", s.numberOfChildren).put("spouseEmployed", s.spouseEmployed).put("birthDate", s.birthDate?.toString() ?: JSONObject.NULL)
        .put("payrollStartDay", s.payrollStartDay).put("restDayWeekday", s.restDayWeekday).put("secondRestDayWeekday", s.secondRestDayWeekday ?: JSONObject.NULL)
        .put("defaultBreakMinutes", s.defaultBreakMinutes).put("breaksArePaid", s.breaksArePaid).put("nightStandardDayHours", s.nightStandardDayHours)
        .put("weeklyStandardHours", s.weeklyStandardHours).put("weeklyOvertimeCapHours", s.weeklyOvertimeCapHours).put("currencyCode", s.currencyCode)
        .put("expectedShiftStartHour", s.expectedShiftStartHour).put("expectedShiftStartMinute", s.expectedShiftStartMinute)

    private fun appChoices(c: AppChoices) = JSONObject()
        .put("language", c.language).put("theme", c.theme).put("showNet", c.showNet)
        .put("breakRemindersEnabled", c.breakRemindersEnabled).put("breakReminderMinutesBefore", c.breakReminderMinutesBefore)
        .put("shiftReminderEnabled", c.shiftReminderEnabled).put("shiftReminderMinutesBefore", c.shiftReminderMinutesBefore)
        .put("shiftSummaryEnabled", c.shiftSummaryEnabled).put("auditRetentionDays", c.auditRetentionDays)
        .put("shiftRetentionYears", c.shiftRetentionYears).put("statCardOrder", JSONArray(c.statCardOrder))

    private fun shift(r: ShiftRecord): JSONObject {
        val s = r.session
        return JSONObject()
            .put("id", s.id.toString()).put("date", s.date.toString()).put("clockIn", s.clockIn.toString()).put("clockOut", s.clockOut?.toString() ?: JSONObject.NULL)
            .put("breakMinutes", s.breakMinutes)
            .put("breaks", JSONArray(s.breaks.map { JSONObject().put("start", it.start.toString()).put("end", it.end?.toString() ?: JSONObject.NULL) }))
            .put("dayType", s.dayType.name).put("isNightShift", s.isNightShift).put("notes", r.notes ?: JSONObject.NULL)
            .put("isManualEntry", r.isManualEntry).put("isAIImported", r.isAIImported).put("workplaceId", r.workplaceId?.toString() ?: JSONObject.NULL)
            .put("modifiedAt", r.modifiedAt.toString())
    }

    private fun auditLine(e: AuditLogEntity) = JSONObject()
        .put("timestamp", Instant.ofEpochMilli(e.timestamp).toString()).put("actor", e.actor).put("action", e.action)
        .put("target", e.targetId ?: JSONObject.NULL).put("beforeHash", e.beforeHash ?: JSONObject.NULL).put("afterHash", e.afterHash ?: JSONObject.NULL)
        .put("metadata", e.metadata?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject.NULL)

    private fun consents(auditLog: List<AuditLogEntity>, current: ConsentRecord?): JSONObject {
        val history = auditLog.filter { it.action == AuditAction.CONSENT_ACCEPTED }.sortedBy { it.timestamp }.map { e ->
            val meta = e.metadata?.let { runCatching { JSONObject(it) }.getOrNull() }
            JSONObject().put("acceptedAt", Instant.ofEpochMilli(e.timestamp).toString()).put("version", meta?.opt("version") ?: JSONObject.NULL)
                .put("appVersion", meta?.opt("appVersion") ?: JSONObject.NULL)
        }
        return JSONObject()
            .put("current", current?.let { JSONObject().put("version", it.version).put("acceptedAt", it.acceptedAt?.toString() ?: JSONObject.NULL).put("appVersion", it.appVersion ?: JSONObject.NULL) } ?: JSONObject.NULL)
            .put("history", JSONArray(history))
    }
}
