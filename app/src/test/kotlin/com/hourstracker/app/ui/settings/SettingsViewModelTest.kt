package com.hourstracker.app.ui.settings

import com.hourstracker.app.data.SettingsRepository
import com.hourstracker.app.data.UserProfile
import com.hourstracker.model.IosCalendar
import com.hourstracker.model.MaritalStatus
import com.hourstracker.model.WorkplaceSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Keeps settings in memory, applying the same clamping the real repository gets from `WorkplaceSettings`. */
private class FakeSettingsRepository(initial: WorkplaceSettings = WorkplaceSettings(hourlyRate = 0.0)) : SettingsRepository {
    private val settingsFlow = MutableStateFlow(initial)
    private val profileFlow = MutableStateFlow(UserProfile())
    var idNumber = ""

    override val settings: StateFlow<WorkplaceSettings> = settingsFlow
    override val profile: StateFlow<UserProfile> = profileFlow

    override fun readIdNumber(): String = idNumber

    override fun save(settings: WorkplaceSettings, profile: UserProfile, idNumber: String?) {
        settingsFlow.value = settings.copy()
        profileFlow.value = profile
        if (idNumber != null) this.idNumber = idNumber
    }
}

class SettingsViewModelTest {
    private val zone = ZoneId.of("UTC")
    private val calendar = IosCalendar(zone, 1, 1, Clock.fixed(Instant.parse("2026-06-20T00:00:00Z"), zone))

    private fun viewModel(repository: FakeSettingsRepository = FakeSettingsRepository()) = SettingsViewModel(repository, calendar) to repository

    @Test
    fun `a new screen has nothing to save`() {
        val (vm, _) = viewModel()
        assertFalse(vm.hasUnsavedChanges)
    }

    @Test
    fun `editing marks changes and saving clears them`() {
        val (vm, repository) = viewModel()
        vm.edit { it.copy(hourlyRate = "55") }
        assertTrue(vm.hasUnsavedChanges)
        vm.save()
        assertFalse(vm.hasUnsavedChanges)
        assertTrue(vm.justSaved)
        assertEquals(55.0, repository.settings.value.hourlyRate)
    }

    @Test
    fun `values outside the allowed range are clamped like on iOS`() {
        val (vm, repository) = viewModel()
        vm.edit {
            it.copy(
                hourlyRate = "-5",
                standardDayHours = "30",
                ot125HoursCap = "-1",
                weeklyStandardHours = "100",
                weeklyOvertimeCapHours = "30",
                payrollStartDay = 31,
                numberOfChildren = 20,
            )
        }
        vm.save()
        val saved = repository.settings.value
        assertEquals(0.0, saved.hourlyRate)
        assertEquals(24.0, saved.standardDayHours)
        assertEquals(0.0, saved.ot125HoursCap)
        assertEquals(60.0, saved.weeklyStandardHours)
        assertEquals(24.0, saved.weeklyOvertimeCapHours)
        assertEquals(28, saved.payrollStartDay)
        assertEquals(15, saved.numberOfChildren)
        // The screen then shows what was stored, not what was typed.
        assertEquals("24", vm.draft.standardDayHours)
    }

    @Test
    fun `a decimal comma is accepted and unreadable text keeps the stored value`() {
        val (vm, repository) = viewModel(FakeSettingsRepository(WorkplaceSettings(hourlyRate = 70.0)))
        vm.edit { it.copy(standardDayHours = "8,5", hourlyRate = "abc") }
        vm.save()
        assertEquals(8.5, repository.settings.value.standardDayHours)
        assertEquals(70.0, repository.settings.value.hourlyRate)
    }

    @Test
    fun `a second rest day equal to the first is dropped`() {
        val (vm, repository) = viewModel()
        vm.edit { it.copy(restDayWeekday = 6, secondRestDayWeekday = 6) }
        assertEquals(0, vm.draft.secondRestDayWeekday)
        vm.edit { it.copy(restDayWeekday = 7, secondRestDayWeekday = 6) }
        vm.save()
        assertEquals(setOf(7, 6), repository.settings.value.restDayWeekdays)
        vm.edit { it.copy(secondRestDayWeekday = 0) }
        vm.save()
        assertNull(repository.settings.value.secondRestDayWeekday)
    }

    @Test
    fun `the ID number is stored as digits only`() {
        val (vm, repository) = viewModel()
        vm.edit { it.copy(idNumber = "123-456-782") }
        vm.save()
        assertEquals("123456782", repository.idNumber)
    }

    @Test
    fun `profile fields are trimmed and the tax profile is saved`() {
        val (vm, repository) = viewModel()
        vm.edit { it.copy(fullName = "  Alex Morgan ", maritalStatus = MaritalStatus.Married, hasChildren = true, numberOfChildren = 2) }
        vm.save()
        assertEquals("Alex Morgan", repository.profile.value.fullName)
        assertEquals(MaritalStatus.Married, repository.settings.value.maritalStatus)
        // 2.25 base + 2 children + 0.5 for a married worker whose spouse is not employed.
        assertEquals(4.75, vm.creditPoints)
    }

    @Test
    fun `the preview of credit points follows the draft before saving`() {
        val (vm, _) = viewModel()
        assertEquals(2.25, vm.creditPoints)
        vm.edit { it.copy(hasChildren = true, numberOfChildren = 3) }
        assertEquals(5.25, vm.creditPoints)
    }
}
