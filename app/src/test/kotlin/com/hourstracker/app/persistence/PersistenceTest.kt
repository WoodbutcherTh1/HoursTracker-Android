package com.hourstracker.app.persistence

import android.app.Application
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.hourstracker.app.HoursTrackerApp
import com.hourstracker.app.MainActivity
import com.hourstracker.app.data.AppFlags
import com.hourstracker.app.data.AppLanguage
import com.hourstracker.app.data.IdCipher
import com.hourstracker.app.data.PrefsSettingsRepository
import com.hourstracker.app.data.SecureIdStore
import com.hourstracker.app.data.UserProfile
import com.hourstracker.model.WorkplaceSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator

/** What must survive leaving the app and coming back: settings, the ID number, the chosen language. */
@RunWith(RobolectricTestRunner::class)
@Config(application = HoursTrackerApp::class, sdk = [35])
class PersistenceTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    // The Android Keystore is not available on the JVM, so these tests use an ordinary AES key.
    private fun idStore() = SecureIdStore(app, IdCipher { key })

    @Test
    fun `changed settings are still there after a restart`() {
        val first = PrefsSettingsRepository(app, idStore())
        first.save(first.settings.value.copy(hourlyRate = 44.0, standardDayHours = 8.6), UserProfile(fullName = "Alex"), idNumber = null)

        val second = PrefsSettingsRepository(app, idStore()) // a new process reads the same file
        assertEquals(44.0, second.settings.value.hourlyRate, 0.0)
        assertEquals(8.6, second.settings.value.standardDayHours, 0.0)
        assertEquals("Alex", second.profile.value.fullName)
    }

    @Test
    fun `out of range values are clamped before they are stored`() {
        val repository = PrefsSettingsRepository(app, idStore())
        repository.save(WorkplaceSettings(hourlyRate = 50.0, standardDayHours = 99.0, payrollStartDay = 40), UserProfile(), idNumber = null)
        val reloaded = PrefsSettingsRepository(app, idStore()).settings.value
        assertEquals(24.0, reloaded.standardDayHours, 0.0)
        assertEquals(28, reloaded.payrollStartDay)
    }

    @Test
    fun `a nine digit ID number is still there after a restart and is not stored as plain text`() {
        val repository = PrefsSettingsRepository(app, idStore())
        repository.save(repository.settings.value, UserProfile(), idNumber = "123456782")

        assertEquals("123456782", PrefsSettingsRepository(app, idStore()).readIdNumber())
        val raw = app.getSharedPreferences("secure_id", 0).getString("id_number", "")
        assertFalse(raw!!.contains("123456782"))
        // The settings file never holds the ID.
        val settingsFile = app.getSharedPreferences("settings", 0).all.values.joinToString()
        assertFalse(settingsFile.contains("123456782"))
    }

    @Test
    fun `clearing the ID number removes it`() {
        val repository = PrefsSettingsRepository(app, idStore())
        repository.save(repository.settings.value, UserProfile(), idNumber = "123456782")
        repository.save(repository.settings.value, UserProfile(), idNumber = "")
        assertEquals("", repository.readIdNumber())
        assertNull(app.getSharedPreferences("secure_id", 0).getString("id_number", null))
    }

    @Test
    fun `the chosen language is remembered`() {
        val flags = AppFlags(app)
        assertEquals(AppLanguage.System, flags.language)
        flags.language = AppLanguage.Hebrew
        assertEquals(AppLanguage.Hebrew, AppFlags(app).language)
    }

    private fun layoutDirectionOf(activity: MainActivity): Int =
        activity.resources.configuration.layoutDirection

    @Test
    fun `switching Hebrew to English to Hebrew restarts the screen without a crash and flips the direction`() {
        val flags = AppFlags(app)
        flags.language = AppLanguage.Hebrew
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertEquals(View.LAYOUT_DIRECTION_RTL, layoutDirectionOf(it)) }

            flags.language = AppLanguage.English
            scenario.recreate()
            scenario.onActivity {
                assertEquals(View.LAYOUT_DIRECTION_LTR, layoutDirectionOf(it))
                assertEquals("en", it.resources.configuration.locales[0].language)
            }

            flags.language = AppLanguage.Hebrew
            scenario.recreate()
            scenario.onActivity {
                assertEquals(View.LAYOUT_DIRECTION_RTL, layoutDirectionOf(it))
                assertTrue(it.resources.configuration.locales[0].language in setOf("iw", "he"))
            }
        }
    }

    @Test
    fun `the theme and the reminder switches are still there after a restart`() {
        val first = PrefsSettingsRepository(app, idStore())
        first.saveThemeMode(com.hourstracker.app.ui.theme.ThemeMode.DARK)
        first.saveBreakRemindersEnabled(false)
        first.saveBreakReminderMinutesBefore(9)
        first.saveShiftReminderEnabled(true)
        first.saveShiftReminderMinutesBefore(45)
        first.saveShiftSummaryEnabled(false)

        val second = PrefsSettingsRepository(app, idStore())
        assertEquals(com.hourstracker.app.ui.theme.ThemeMode.DARK, second.themeMode.value)
        assertEquals(false, second.breakRemindersEnabled.value)
        assertEquals(9, second.breakReminderMinutesBefore.value)
        assertEquals(true, second.shiftReminderEnabled.value)
        assertEquals(45, second.shiftReminderMinutesBefore.value)
        assertEquals(false, second.shiftSummaryEnabled.value)
    }
}
