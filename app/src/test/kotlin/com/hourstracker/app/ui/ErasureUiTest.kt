package com.hourstracker.app.ui

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.core.app.NotificationCompat
import com.hourstracker.app.data.AppContainer
import com.hourstracker.app.data.DataEraser
import com.hourstracker.app.data.IdCipher
import com.hourstracker.app.data.UserProfile
import com.hourstracker.app.screenshots.ScreenshotApp
import com.hourstracker.app.screenshots.ScreenshotFixture
import com.hourstracker.app.ui.settings.PrivacySettingsSection
import com.hourstracker.data.AuditAction
import com.hourstracker.data.ShiftRecord
import com.hourstracker.model.BreakInterval
import com.hourstracker.model.WorkSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.util.UUID

/** "Delete all my data": everything goes, one personal-data-free line remains, and the app starts again from the first screen. */
@RunWith(RobolectricTestRunner::class)
@Config(application = ScreenshotApp::class, sdk = [35])
class ErasureUiTest {
    @get:Rule
    val compose = createComposeRule()
    private val fixture = ScreenshotFixture(compose)
    private val container get() = fixture.container
    private val app get() = fixture.app

    @Before
    fun setUp() {
        fixture.start()
        val day = Instant.parse("2026-06-16T00:00:00Z")
        val session = WorkSession(UUID(4, 4), day, day.plusSeconds(8 * 3600), day.plusSeconds(16 * 3600), 30, listOf(BreakInterval(day.plusSeconds(12 * 3600), day.plusSeconds(12 * 3600 + 1800))))
        runBlocking { container.shifts.upsert(ShiftRecord(session, notes = "private note")) }
        container.settings.save(container.settings.settings.value.copy(hourlyRate = 61.0), UserProfile("Dana Levi", "E-1", "Cafe", "Agency"), idNumber = "123456782")
        container.flags.acceptLegal()
        container.flags.finishOnboarding()
        container.settings.saveShiftReminderEnabled(true)
        File(app.cacheDir, "exports").apply { mkdirs() }.resolve("old.pdf").writeText("report")
        File(app.filesDir, "x.txt").writeText("x")
        (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(
            1, NotificationCompat.Builder(app, "shift_timer").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("t").build(),
        )
        repeat(40) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(20) }
    }

    private fun freshContainer() = AppContainer(app, calendarFactory = { ScreenshotFixture.FIXED_CALENDAR }, seedMockShifts = false, idCipher = IdCipher { ScreenshotFixture.TEST_KEY })

    @Test
    fun `everything is gone afterwards and nothing failed`() {
        assertEquals(emptyList<String>(), runBlocking { container.eraseAllData() })
        val fresh = freshContainer()
        assertEquals(0, runBlocking { fresh.shifts.shifts.first().size })
        assertEquals("", fresh.settings.profile.value.fullName)
        assertEquals(0.0, fresh.settings.settings.value.hourlyRate, 0.0)
        assertEquals("", fresh.settings.readIdNumber())
        assertEquals(0, fresh.flags.acceptedLegalVersion.value)
        assertFalse(fresh.flags.onboardingDone.value)
        assertFalse(fresh.settings.shiftReminderEnabled.value)
    }

    @Test
    fun `the breaks and the cache and files are removed too`() {
        runBlocking { container.eraseAllData() }
        assertEquals(0, File(app.cacheDir, "exports").listFiles()?.size ?: 0)
        assertEquals(0, app.filesDir.listFiles()?.size ?: 0)
    }

    @Test
    fun `reminders and notifications are switched off`() {
        val alarms = shadowOf(app.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
        runBlocking { container.eraseAllData() }
        assertEquals(null, alarms.peekNextScheduledAlarm())
        assertEquals(0, shadowOf(app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).allNotifications.size)
    }

    @Test
    fun `exactly one line is left in the activity log and it holds nothing personal`() {
        runBlocking { container.eraseAllData() }
        val entries = runBlocking { container.audit.entries() }
        assertEquals(listOf(AuditAction.ERASURE_COMPLETED), entries.map { it.action })
        val text = entries.single().let { "${it.targetId} ${it.beforeHash} ${it.afterHash} ${it.metadata}" }
        listOf("Dana", "private", "123456782", "Cafe").forEach { assertFalse(text.contains(it)) }
        assertEquals(0, JSONObject(entries.single().metadata!!).getJSONArray("failedSteps").length())
    }

    @Test
    fun `every preferences file the app creates is on the eraser's list`() {
        val used = File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }
            .flatMap { Regex("""getSharedPreferences\("([^"]+)"""").findAll(it.readText()).map { m -> m.groupValues[1] } }.toSet()
        assertTrue("unlisted: ${used - DataEraser.PREFERENCE_FILES.toSet()}", DataEraser.PREFERENCE_FILES.toSet().containsAll(used))
    }

    @Test
    fun `the dialog needs the word before the red button works, then wipes and restarts`() {
        var restarted = false
        fixture.show { PrivacySettingsSection(onOpenActivityLog = {}, onRestart = { restarted = true }) }
        compose.onNodeWithText("Delete all my data").performClick()
        compose.onNodeWithText("Delete everything").assertIsNotEnabled()
        compose.onNodeWithText("Type DELETE to confirm", substring = true).performTextInput("nope")
        compose.onNodeWithText("Delete everything").assertIsNotEnabled()
        compose.onNodeWithText("nope").performTextInput("") // keep focus
        compose.onNodeWithText("nope").performTextClearAndType("delete")
        compose.onNodeWithText("Delete everything").assertIsEnabled().performClick()
        repeat(150) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
            if (restarted) return@repeat
            Thread.sleep(20)
        }
        assertTrue(restarted)
        assertEquals(0, runBlocking { freshContainer().shifts.shifts.first().size })
    }

    @Test
    fun `cancelling the dialog deletes nothing`() {
        fixture.show { PrivacySettingsSection(onOpenActivityLog = {}) }
        compose.onNodeWithText("Delete all my data").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        assertEquals(1, runBlocking { container.shifts.shifts.first().size })
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.performTextClearAndType(text: String) {
    performTextReplacement(text)
}
