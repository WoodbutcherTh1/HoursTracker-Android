package com.hourstracker.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
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

    /** The ID number is read on demand and never held in a flow. */
    fun readIdNumber(): String

    fun save(settings: WorkplaceSettings, profile: UserProfile, idNumber: String? = null)
}

/** SharedPreferences-backed settings. Values pass through `WorkplaceSettings`, so the iOS clamping applies. */
class PrefsSettingsRepository(context: Context, private val idStore: SecureIdStore) : SettingsRepository {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val settingsFlow = MutableStateFlow(load())
    private val profileFlow = MutableStateFlow(loadProfile())

    override val settings: StateFlow<WorkplaceSettings> = settingsFlow.asStateFlow()
    override val profile: StateFlow<UserProfile> = profileFlow.asStateFlow()

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
            val second = clamped.secondRestDayWeekday
            if (second == null) remove("secondRestDayWeekday") else putInt("secondRestDayWeekday", second)
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

    private fun load(): WorkplaceSettings {
        fun double(key: String, default: Double) = prefs.getString(key, null)?.toDoubleOrNull() ?: default
        return WorkplaceSettings(
            hourlyRate = double("hourlyRate", 0.0),
            dailyGasAllowance = double("dailyGasAllowance", 35.0),
            standardDayHours = double("standardDayHours", 8.6),
            ot125HoursCap = double("ot125HoursCap", 2.0),
            maritalStatus = MaritalStatus.fromRaw(prefs.getString("maritalStatus", null) ?: "single") ?: MaritalStatus.Single,
            hasChildren = prefs.getBoolean("hasChildren", false),
            numberOfChildren = prefs.getInt("numberOfChildren", 0),
            spouseEmployed = prefs.getBoolean("spouseEmployed", false),
            birthDate = if (prefs.contains("birthDate")) Instant.ofEpochMilli(prefs.getLong("birthDate", 0)) else null,
            payrollStartDay = prefs.getInt("payrollStartDay", 1),
            restDayWeekday = prefs.getInt("restDayWeekday", 7),
            secondRestDayWeekday = if (prefs.contains("secondRestDayWeekday")) prefs.getInt("secondRestDayWeekday", 6) else null,
            defaultBreakMinutes = prefs.getInt("defaultBreakMinutes", 0),
            breaksArePaid = prefs.getBoolean("breaksArePaid", false),
            nightStandardDayHours = double("nightStandardDayHours", 7.0),
            weeklyStandardHours = double("weeklyStandardHours", 42.0),
            weeklyOvertimeCapHours = double("weeklyOvertimeCapHours", 12.0),
            currencyCode = prefs.getString("currencyCode", null) ?: "ILS",
            expectedShiftStartHour = prefs.getInt("expectedShiftStartHour", 8),
            expectedShiftStartMinute = prefs.getInt("expectedShiftStartMinute", 0),
        )
    }

    private fun loadProfile() = UserProfile(
        fullName = prefs.getString("fullName", "").orEmpty(),
        employeeNumber = prefs.getString("employeeNumber", "").orEmpty(),
        workplaceName = prefs.getString("workplaceName", "").orEmpty(),
        contractorName = prefs.getString("contractorName", "").orEmpty(),
    )
}
