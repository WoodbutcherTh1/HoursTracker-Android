package com.hourstracker.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.hourstracker.app.data.SettingsRepository
import com.hourstracker.app.data.UserProfile
import com.hourstracker.app.util.formatDecimal
import com.hourstracker.app.util.parseDecimal
import com.hourstracker.model.IosCalendar
import com.hourstracker.model.MaritalStatus
import com.hourstracker.model.WorkplaceSettings
import java.time.Instant

/** The editable copy of the settings. Numbers are text while they are being typed. */
data class SettingsDraft(
    val fullName: String,
    val idNumber: String,
    val employeeNumber: String,
    val workplaceName: String,
    val contractorName: String,
    val hourlyRate: String,
    val dailyGasAllowance: String,
    val standardDayHours: String,
    val ot125HoursCap: String,
    val weeklyStandardHours: String,
    val weeklyOvertimeCapHours: String,
    val restDayWeekday: Int,
    /** 0 means "none". */
    val secondRestDayWeekday: Int,
    val breaksArePaid: Boolean,
    val defaultBreakMinutes: String,
    val currencyCode: String,
    val expectedShiftStartHour: Int,
    val expectedShiftStartMinute: Int,
    val payrollStartDay: Int,
    val birthDate: Instant?,
    val maritalStatus: MaritalStatus,
    val hasChildren: Boolean,
    val numberOfChildren: Int,
    val spouseEmployed: Boolean,
)

class SettingsViewModel(
    private val repository: SettingsRepository,
    val calendar: IosCalendar,
) : ViewModel() {
    private var saved: SettingsDraft = draftFrom(repository.settings.value, repository.profile.value, repository.readIdNumber())

    var draft: SettingsDraft by mutableStateOf(saved)
        private set

    /** True after Save until the next edit. */
    var justSaved: Boolean by mutableStateOf(false)
        private set

    var isEditingIdNumber: Boolean by mutableStateOf(false)

    val hasUnsavedChanges: Boolean get() = draft != saved

    val creditPoints: Double get() = toSettings(draft).creditPoints

    fun edit(change: (SettingsDraft) -> SettingsDraft) {
        justSaved = false
        draft = change(draft).let { next ->
            // A second rest day equal to the first is dropped, as in the iOS picker.
            if (next.secondRestDayWeekday == next.restDayWeekday) next.copy(secondRestDayWeekday = 0) else next
        }
    }

    fun save() {
        val settings = toSettings(draft)
        repository.save(
            settings = settings,
            profile = UserProfile(draft.fullName.trim(), draft.employeeNumber.trim(), draft.workplaceName.trim(), draft.contractorName.trim()),
            idNumber = draft.idNumber.filter { it.isDigit() },
        )
        // Show what was actually stored (clamped), like iOS does after saving.
        saved = draftFrom(repository.settings.value, repository.profile.value, repository.readIdNumber())
        draft = saved
        justSaved = true
    }

    /** Throws the edits away and shows what is stored. */
    fun discard() {
        draft = saved
        justSaved = false
    }

    /** The settings this draft means, with unparsable numbers left at the stored value. */
    private fun toSettings(d: SettingsDraft): WorkplaceSettings {
        val current = repository.settings.value
        return WorkplaceSettings(
            hourlyRate = parseDecimal(d.hourlyRate) ?: current.hourlyRate,
            dailyGasAllowance = parseDecimal(d.dailyGasAllowance) ?: current.dailyGasAllowance,
            standardDayHours = parseDecimal(d.standardDayHours) ?: current.standardDayHours,
            ot125HoursCap = parseDecimal(d.ot125HoursCap) ?: current.ot125HoursCap,
            maritalStatus = d.maritalStatus,
            hasChildren = d.hasChildren,
            numberOfChildren = d.numberOfChildren,
            spouseEmployed = d.spouseEmployed,
            birthDate = d.birthDate,
            payrollStartDay = d.payrollStartDay,
            restDayWeekday = d.restDayWeekday,
            secondRestDayWeekday = d.secondRestDayWeekday.takeIf { it != 0 },
            defaultBreakMinutes = d.defaultBreakMinutes.toIntOrNull() ?: current.defaultBreakMinutes,
            breaksArePaid = d.breaksArePaid,
            nightStandardDayHours = current.nightStandardDayHours,
            weeklyStandardHours = parseDecimal(d.weeklyStandardHours) ?: current.weeklyStandardHours,
            weeklyOvertimeCapHours = parseDecimal(d.weeklyOvertimeCapHours) ?: current.weeklyOvertimeCapHours,
            currencyCode = d.currencyCode,
            expectedShiftStartHour = d.expectedShiftStartHour,
            expectedShiftStartMinute = d.expectedShiftStartMinute,
        )
    }

    private companion object {
        fun draftFrom(s: WorkplaceSettings, p: UserProfile, idNumber: String) = SettingsDraft(
            fullName = p.fullName,
            idNumber = idNumber,
            employeeNumber = p.employeeNumber,
            workplaceName = p.workplaceName,
            contractorName = p.contractorName,
            hourlyRate = formatDecimal(s.hourlyRate),
            dailyGasAllowance = formatDecimal(s.dailyGasAllowance),
            standardDayHours = formatDecimal(s.standardDayHours),
            ot125HoursCap = formatDecimal(s.ot125HoursCap),
            weeklyStandardHours = formatDecimal(s.weeklyStandardHours),
            weeklyOvertimeCapHours = formatDecimal(s.weeklyOvertimeCapHours),
            restDayWeekday = s.restDayWeekday,
            secondRestDayWeekday = s.secondRestDayWeekday ?: 0,
            breaksArePaid = s.breaksArePaid,
            defaultBreakMinutes = s.defaultBreakMinutes.toString(),
            currencyCode = s.currencyCode,
            expectedShiftStartHour = s.expectedShiftStartHour,
            expectedShiftStartMinute = s.expectedShiftStartMinute,
            payrollStartDay = s.payrollStartDay,
            birthDate = s.birthDate,
            maritalStatus = s.maritalStatus,
            hasChildren = s.hasChildren,
            numberOfChildren = s.numberOfChildren,
            spouseEmployed = s.spouseEmployed,
        )
    }
}
