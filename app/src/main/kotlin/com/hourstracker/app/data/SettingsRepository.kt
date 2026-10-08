package com.hourstracker.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.hourstracker.app.domain.StatType
import com.hourstracker.app.ui.theme.ThemeMode
import com.hourstracker.model.MaritalStatus
import com.hourstracker.model.WorkplaceSettings
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Pay settings (core-model) plus the worker profile, kept on the device. */
interface SettingsRepository {
    val settings: StateFlow<WorkplaceSettings>
    val profile: StateFlow<UserProfile>
    val statCardOrder: StateFlow<List<StatType>>
    val themeMode: StateFlow<ThemeMode>
    val breakRemindersEnabled: StateFlow<Boolean>
    val breakReminderMinutesBefore: StateFlow<Int>

    /** A reminder before the usual shift start time, on working days. Off until the worker turns it on. */
    val shiftReminderEnabled: StateFlow<Boolean>
    val shiftReminderMinutesBefore: StateFlow<Int>

    /** A notification with hours and pay after clocking out from the notification. */
    val shiftSummaryEnabled: StateFlow<Boolean>

    /** How long the activity log is kept, in days. One of [AUDIT_RETENTION_CHOICES]; default 365. */
    val auditRetentionDays: StateFlow<Int>

    /** Shifts older than this many years are deleted when the app starts. 0 means never (the default). */
    val shiftRetentionYears: StateFlow<Int>

    /** The ID number is read on demand and never held in a flow. */
    fun readIdNumber(): String

    fun save(settings: WorkplaceSettings, profile: UserProfile, idNumber: String?)
    fun saveStatCardOrder(order: List<StatType>)
    fun saveThemeMode(mode: ThemeMode)
    fun saveBreakRemindersEnabled(enabled: Boolean)
    fun saveBreakReminderMinutesBefore(minutes: Int)
    fun saveShiftReminderEnabled(enabled: Boolean)
    fun saveShiftReminderMinutesBefore(minutes: Int)
    fun saveShiftSummaryEnabled(enabled: Boolean)
    fun saveAuditRetentionDays(days: Int)
    fun saveShiftRetentionYears(years: Int)

    companion object {
        val AUDIT_RETENTION_CHOICES = listOf(90, 365, 730, 1825)
        val SHIFT_RETENTION_CHOICES = listOf(0, 1, 2, 5, 10)
    }
}

/** SharedPreferences-backed settings. Values pass through `WorkplaceSettings`, so the iOS clamping applies. */
class PrefsSettingsRepository(context: Context, private val idStore: SecureIdStore) : SettingsRepository {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val settingsFlow = MutableStateFlow(load())
    private val profileFlow = MutableStateFlow(loadProfile())
    private val statCardOrderFlow = MutableStateFlow(loadStatCardOrder())
    private val themeModeFlow = MutableStateFlow(loadThemeMode())
    private val breakRemindersEnabledFlow = MutableStateFlow(prefs.getBoolean("breakRemindersEnabled", true))
    private val breakReminderMinutesBeforeFlow = MutableStateFlow(prefs.getInt("breakReminderMinutesBefore", 5))

    private val shiftReminderEnabledFlow = MutableStateFlow(prefs.getBoolean("shiftReminderEnabled", false))
    private val shiftReminderMinutesBeforeFlow = MutableStateFlow(prefs.getInt("shiftReminderMinutesBefore", 15))
    private val shiftSummaryEnabledFlow = MutableStateFlow(prefs.getBoolean("shiftSummaryEnabled", true))

    private val auditRetentionDaysFlow = MutableStateFlow(prefs.getInt("auditRetentionDays", 365).takeIf { it in SettingsRepository.AUDIT_RETENTION_CHOICES } ?: 365)
    private val shiftRetentionYearsFlow = MutableStateFlow(prefs.getInt("shiftRetentionYears", 0).takeIf { it in SettingsRepository.SHIFT_RETENTION_CHOICES } ?: 0)

    override val settings: StateFlow<WorkplaceSettings> = settingsFlow.asStateFlow()
    override val profile: StateFlow<UserProfile> = profileFlow.asStateFlow()
    override val statCardOrder: StateFlow<List<StatType>> = statCardOrderFlow.asStateFlow()
    override val themeMode: StateFlow<ThemeMode> = themeModeFlow.asStateFlow()
    override val breakRemindersEnabled: StateFlow<Boolean> = breakRemindersEnabledFlow.asStateFlow()
    override val breakReminderMinutesBefore: StateFlow<Int> = breakReminderMinutesBeforeFlow.asStateFlow()
    override val shiftReminderEnabled: StateFlow<Boolean> = shiftReminderEnabledFlow.asStateFlow()
    override val shiftReminderMinutesBefore: StateFlow<Int> = shiftReminderMinutesBeforeFlow.asStateFlow()
    override val shiftSummaryEnabled: StateFlow<Boolean> = shiftSummaryEnabledFlow.asStateFlow()
    override val auditRetentionDays: StateFlow<Int> = auditRetentionDaysFlow.asStateFlow()
    override val shiftRetentionYears: StateFlow<Int> = shiftRetentionYearsFlow.asStateFlow()

    override fun readIdNumber(): String = idStore.read().orEmpty()

    override fun save(settings: WorkplaceSettings, profile: UserProfile, idNumber: String?) {
        // Re-create through the constructor so the validated fields are clamped exactly as on iOS.
        val clamped = settings.copy()
        prefs.edit {
            putString("hourlyRate", clamped.hourlyRate.toString())
            putString("dailyGasAllowance", clamped.dailyGasAllowance.toString())
            putString("standardDayHours", clamped.standardDayHours.toString())
            putString("ot125HoursCap", clamped.ot125HoursCap.toString())
            putString("maritalStatus", clamped.maritalStatus.raw)
            putBoolean("hasChildren", clamped.hasChildren)
            putInt("numberOfChildren", clamped.numberOfChildren)
            putBoolean("spouseEmployed", clamped.spouseEmployed)
            val born = clamped.birthDate
            if (born == null) remove("birthDate") else putLong("birthDate", born.toEpochMilli())
            putInt("payrollStartDay", clamped.payrollStartDay)
            putInt("restDayWeekday", clamped.restDayWeekday)
            clamped.secondRestDayWeekday?.let { putInt("secondRestDayWeekday", it) } ?: remove("secondRestDayWeekday")
            putInt("defaultBreakMinutes", clamped.defaultBreakMinutes)
            putBoolean("breaksArePaid", clamped.breaksArePaid)
            putString("nightStandardDayHours", clamped.nightStandardDayHours.toString())
            putString("weeklyStandardHours", clamped.weeklyStandardHours.toString())
            putString("weeklyOvertimeCapHours", clamped.weeklyOvertimeCapHours.toString())
            putString("currencyCode", clamped.currencyCode)
            putInt("expectedShiftStartHour", clamped.expectedShiftStartHour)
            putInt("expectedShiftStartMinute", clamped.expectedShiftStartMinute)
            putString("fullName", profile.fullName)
            putString("employeeNumber", profile.employeeNumber)
            putString("workplaceName", profile.workplaceName)
            putString("contractorName", profile.contractorName)
        }
        if (idNumber != null) idStore.write(idNumber)
        settingsFlow.value = clamped
        profileFlow.value = profile
    }

    override fun saveStatCardOrder(order: List<StatType>) {
        prefs.edit {
            putString("statCardOrder", order.joinToString(separator = ",") { it.name })
        }
        statCardOrderFlow.value = order
    }

    override fun saveThemeMode(mode: ThemeMode) {
        prefs.edit {
            putString("themeMode", mode.name.lowercase())
        }
        themeModeFlow.value = mode
    }

    override fun saveBreakRemindersEnabled(enabled: Boolean) {
        prefs.edit {
            putBoolean("breakRemindersEnabled", enabled)
        }
        breakRemindersEnabledFlow.value = enabled
    }

    override fun saveBreakReminderMinutesBefore(minutes: Int) {
        prefs.edit {
            putInt("breakReminderMinutesBefore", minutes.coerceIn(1, 30))
        }
        breakReminderMinutesBeforeFlow.value = minutes.coerceIn(1, 30)
    }

    override fun saveShiftReminderEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("shiftReminderEnabled", enabled) }
        shiftReminderEnabledFlow.value = enabled
    }

    override fun saveShiftReminderMinutesBefore(minutes: Int) {
        val clamped = minutes.coerceIn(5, 120)
        prefs.edit { putInt("shiftReminderMinutesBefore", clamped) }
        shiftReminderMinutesBeforeFlow.value = clamped
    }

    override fun saveShiftSummaryEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("shiftSummaryEnabled", enabled) }
        shiftSummaryEnabledFlow.value = enabled
    }

    override fun saveAuditRetentionDays(days: Int) {
        val choice = days.takeIf { it in SettingsRepository.AUDIT_RETENTION_CHOICES } ?: return
        prefs.edit { putInt("auditRetentionDays", choice) }
        auditRetentionDaysFlow.value = choice
    }

    override fun saveShiftRetentionYears(years: Int) {
        val choice = years.takeIf { it in SettingsRepository.SHIFT_RETENTION_CHOICES } ?: return
        prefs.edit { putInt("shiftRetentionYears", choice) }
        shiftRetentionYearsFlow.value = choice
    }

    private fun load(): WorkplaceSettings {
        fun double(key: String, default: Double) = prefs.getString(key, null)?.toDoubleOrNull() ?: default
        fun int(key: String, default: Int) = prefs.getInt(key, default)

        return WorkplaceSettings(
            hourlyRate = double("hourlyRate", 0.0),
            dailyGasAllowance = double("dailyGasAllowance", 35.0),
            standardDayHours = double("standardDayHours", 8.6),
            ot125HoursCap = double("ot125HoursCap", 2.0),
            locationRadiusMeters = double("locationRadiusMeters", 150.0),
            maritalStatus = MaritalStatus.fromRaw(prefs.getString("maritalStatus", null) ?: "single") ?: MaritalStatus.Single,
            hasChildren = prefs.getBoolean("hasChildren", false),
            numberOfChildren = prefs.getInt("numberOfChildren", 0),
            spouseEmployed = prefs.getBoolean("spouseEmployed", false),
            birthDate = if (prefs.contains("birthDate")) Instant.ofEpochMilli(prefs.getLong("birthDate", 0)) else null,
            payrollStartDay = int("payrollStartDay", 1),
            restDayWeekday = int("restDayWeekday", 7),
            secondRestDayWeekday = if (prefs.contains("secondRestDayWeekday")) prefs.getInt("secondRestDayWeekday", 6) else null,
            defaultBreakMinutes = prefs.getInt("defaultBreakMinutes", 0),
            breaksArePaid = prefs.getBoolean("breaksArePaid", false),
            nightStandardDayHours = double("nightStandardDayHours", 7.0),
            weeklyStandardHours = double("weeklyStandardHours", 42.0),
            weeklyOvertimeCapHours = double("weeklyOvertimeCapHours", 12.0),
            currencyCode = prefs.getString("currencyCode", null) ?: "ILS",
            expectedShiftStartHour = int("expectedShiftStartHour", 8),
            expectedShiftStartMinute = int("expectedShiftStartMinute", 0),
        )
    }

    private fun loadProfile() = UserProfile(
        fullName = prefs.getString("fullName", "").orEmpty(),
        employeeNumber = prefs.getString("employeeNumber", "").orEmpty(),
        workplaceName = prefs.getString("workplaceName", "").orEmpty(),
        contractorName = prefs.getString("contractorName", "").orEmpty(),
    )

    private fun loadStatCardOrder(): List<StatType> {
        val orderString = prefs.getString("statCardOrder", null)
        return if (orderString != null) {
            orderString.split(",").mapNotNull { name ->
                try {
                    StatType.valueOf(name)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }.takeIf { it.size == 3 } ?: listOf(StatType.TODAY, StatType.WEEK, StatType.MONTH)
        } else {
            listOf(StatType.TODAY, StatType.WEEK, StatType.MONTH)
        }
    }

    private fun loadThemeMode(): ThemeMode {
        return ThemeMode.fromString(prefs.getString("themeMode", null))
    }
}
