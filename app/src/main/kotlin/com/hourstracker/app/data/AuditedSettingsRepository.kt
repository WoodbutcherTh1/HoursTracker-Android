package com.hourstracker.app.data

import com.hourstracker.app.domain.StatType
import com.hourstracker.app.ui.theme.ThemeMode
import com.hourstracker.data.AuditAction
import com.hourstracker.data.AuditLog
import com.hourstracker.model.WorkplaceSettings

/**
 * A [SettingsRepository] that records each change in the activity log. Only the NAMES of the changed fields are written.
 * The ID number is reported as changed or not and is never hashed (a hash of a nine-digit number can be guessed).
 */
class AuditedSettingsRepository(private val delegate: SettingsRepository, private val audit: AuditLog) : SettingsRepository by delegate {
    override fun save(settings: WorkplaceSettings, profile: UserProfile, idNumber: String?) {
        val oldSettings = delegate.settings.value
        val oldProfile = delegate.profile.value
        val idChanged = idNumber != null && idNumber != delegate.readIdNumber()
        delegate.save(settings, profile, idNumber)
        val newSettings = delegate.settings.value
        val newProfile = delegate.profile.value
        val fields = changedFields(oldSettings, newSettings) + changedProfileFields(oldProfile, newProfile) + if (idChanged) listOf("idNumber") else emptyList()
        if (fields.isEmpty()) return
        audit.log(
            AuditAction.SETTINGS_UPDATE,
            targetId = TARGET_PAY,
            before = AuditLog.hash(oldSettings, oldProfile),
            after = AuditLog.hash(newSettings, newProfile),
            metadata = mapOf("fields" to fields),
        )
    }

    override fun saveStatCardOrder(order: List<StatType>) = changed("statCardOrder", delegate.statCardOrder.value != order) { delegate.saveStatCardOrder(order) }

    override fun saveThemeMode(mode: ThemeMode) = changed("themeMode", delegate.themeMode.value != mode) { delegate.saveThemeMode(mode) }

    override fun saveBreakRemindersEnabled(enabled: Boolean) = changed("breakRemindersEnabled", delegate.breakRemindersEnabled.value != enabled) { delegate.saveBreakRemindersEnabled(enabled) }

    override fun saveBreakReminderMinutesBefore(minutes: Int) = changed("breakReminderMinutesBefore", delegate.breakReminderMinutesBefore.value != minutes.coerceIn(1, 30)) { delegate.saveBreakReminderMinutesBefore(minutes) }

    override fun saveShiftReminderEnabled(enabled: Boolean) = changed("shiftReminderEnabled", delegate.shiftReminderEnabled.value != enabled) { delegate.saveShiftReminderEnabled(enabled) }

    override fun saveShiftReminderMinutesBefore(minutes: Int) = changed("shiftReminderMinutesBefore", delegate.shiftReminderMinutesBefore.value != minutes.coerceIn(5, 120)) { delegate.saveShiftReminderMinutesBefore(minutes) }

    override fun saveShiftSummaryEnabled(enabled: Boolean) = changed("shiftSummaryEnabled", delegate.shiftSummaryEnabled.value != enabled) { delegate.saveShiftSummaryEnabled(enabled) }

    override fun saveAuditRetentionDays(days: Int) = changed("auditRetentionDays", delegate.auditRetentionDays.value != days) { delegate.saveAuditRetentionDays(days) }

    override fun saveShiftRetentionYears(years: Int) = changed("shiftRetentionYears", delegate.shiftRetentionYears.value != years) { delegate.saveShiftRetentionYears(years) }

    private inline fun changed(field: String, differs: Boolean, apply: () -> Unit) {
        apply()
        if (differs) audit.log(AuditAction.SETTINGS_UPDATE, targetId = TARGET_APP, metadata = mapOf("fields" to listOf(field)))
    }

    private fun changedFields(a: WorkplaceSettings, b: WorkplaceSettings): List<String> = buildList {
        if (a.hourlyRate != b.hourlyRate) add("hourlyRate")
        if (a.dailyGasAllowance != b.dailyGasAllowance) add("dailyGasAllowance")
        if (a.standardDayHours != b.standardDayHours) add("standardDayHours")
        if (a.ot125HoursCap != b.ot125HoursCap) add("ot125HoursCap")
        if (a.maritalStatus != b.maritalStatus) add("maritalStatus")
        if (a.hasChildren != b.hasChildren) add("hasChildren")
        if (a.numberOfChildren != b.numberOfChildren) add("numberOfChildren")
        if (a.spouseEmployed != b.spouseEmployed) add("spouseEmployed")
        if (a.birthDate != b.birthDate) add("birthDate")
        if (a.payrollStartDay != b.payrollStartDay) add("payrollStartDay")
        if (a.restDayWeekday != b.restDayWeekday) add("restDayWeekday")
        if (a.secondRestDayWeekday != b.secondRestDayWeekday) add("secondRestDayWeekday")
        if (a.defaultBreakMinutes != b.defaultBreakMinutes) add("defaultBreakMinutes")
        if (a.breaksArePaid != b.breaksArePaid) add("breaksArePaid")
        if (a.nightStandardDayHours != b.nightStandardDayHours) add("nightStandardDayHours")
        if (a.weeklyStandardHours != b.weeklyStandardHours) add("weeklyStandardHours")
        if (a.weeklyOvertimeCapHours != b.weeklyOvertimeCapHours) add("weeklyOvertimeCapHours")
        if (a.currencyCode != b.currencyCode) add("currencyCode")
        if (a.expectedShiftStartHour != b.expectedShiftStartHour) add("expectedShiftStartHour")
        if (a.expectedShiftStartMinute != b.expectedShiftStartMinute) add("expectedShiftStartMinute")
    }

    private fun changedProfileFields(a: UserProfile, b: UserProfile): List<String> = buildList {
        if (a.fullName != b.fullName) add("fullName")
        if (a.employeeNumber != b.employeeNumber) add("employeeNumber")
        if (a.workplaceName != b.workplaceName) add("workplaceName")
        if (a.contractorName != b.contractorName) add("contractorName")
    }

    private companion object {
        const val TARGET_PAY = "pay_settings"
        const val TARGET_APP = "app_preferences"
    }
}
