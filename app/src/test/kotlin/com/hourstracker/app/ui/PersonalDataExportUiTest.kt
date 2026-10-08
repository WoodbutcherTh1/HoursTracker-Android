package com.hourstracker.app.ui

import android.content.Intent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hourstracker.app.data.UserProfile
import com.hourstracker.app.domain.PersonalDataExport
import com.hourstracker.app.screenshots.ScreenshotApp
import com.hourstracker.app.screenshots.ScreenshotFixture
import com.hourstracker.app.ui.settings.PrivacySettingsSection
import com.hourstracker.data.AuditAction
import com.hourstracker.data.ShiftRecord
import com.hourstracker.model.BreakInterval
import com.hourstracker.model.WorkSession
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

/** "Download my data": a complete, versioned JSON file that never contains the ID number. */
@RunWith(RobolectricTestRunner::class)
@Config(application = ScreenshotApp::class, sdk = [35])
class PersonalDataExportUiTest {
    @get:Rule
    val compose = createComposeRule()
    private val fixture = ScreenshotFixture(compose)
    private val container get() = fixture.container
    private val app get() = fixture.app

    @Before
    fun setUp() {
        fixture.start()
        val day = Instant.parse("2026-06-16T00:00:00Z")
        val session = WorkSession(UUID(8, 8), day, day.plusSeconds(8 * 3600), day.plusSeconds(16 * 3600), 30, listOf(BreakInterval(day.plusSeconds(12 * 3600), day.plusSeconds(12 * 3600 + 1800))))
        runBlocking { container.shifts.upsert(ShiftRecord(session, notes = "remember the keys")) }
        container.settings.save(container.settings.settings.value.copy(hourlyRate = 61.5), UserProfile("Dana Levi", "E-17", "Corner Cafe", "Staffing Ltd"), idNumber = "123456782")
        container.acceptLegal()
    }

    private fun export(): JSONObject = JSONObject(runBlocking { container.buildPersonalDataExport() })

    @Test
    fun `the file says what it is and which version of the format it uses`() {
        val json = export()
        assertEquals(PersonalDataExport.SCHEMA, json.getString("schema"))
        assertEquals(PersonalDataExport.SCHEMA_VERSION, json.getInt("schemaVersion"))
        assertEquals("2026-06-17T10:30:00Z", json.getString("exportedAt"))
        assertEquals("android", json.getJSONObject("app").getString("platform"))
    }

    @Test
    fun `the ID number is never in the file, and the file says so`() {
        val text = runBlocking { container.buildPersonalDataExport() }
        assertFalse(text.contains("123456782"))
        assertEquals("idNumber", JSONObject(text).getJSONArray("notIncluded").getString(0))
        // The owner did set one: it is really stored.
        assertEquals("123456782", container.settings.readIdNumber())
    }

    @Test
    fun `shifts come with their breaks and notes`() {
        val shift = export().getJSONArray("shifts").getJSONObject(0)
        assertEquals("2026-06-16T08:00:00Z", shift.getString("clockIn"))
        assertEquals(30, shift.getInt("breakMinutes"))
        assertEquals(1, shift.getJSONArray("breaks").length())
        assertEquals("remember the keys", shift.getString("notes"))
        assertEquals("REGULAR", shift.getString("dayType").uppercase())
    }

    @Test
    fun `profile, pay settings and app choices are there`() {
        val json = export()
        assertEquals("Dana Levi", json.getJSONObject("profile").getString("fullName"))
        assertEquals("Corner Cafe", json.getJSONObject("profile").getString("workplaceName"))
        assertEquals(61.5, json.getJSONObject("paySettings").getDouble("hourlyRate"), 0.0)
        assertTrue(json.getJSONObject("appChoices").has("theme"))
        assertTrue(json.getJSONObject("appChoices").has("shiftRetentionYears"))
    }

    @Test
    fun `the activity log and the consents are included`() {
        val json = export()
        assertTrue(json.getJSONArray("auditLog").length() >= 3)
        val consents = json.getJSONObject("consents")
        assertEquals(1, consents.getJSONObject("current").getInt("version"))
        assertEquals(1, consents.getJSONArray("history").length())
    }

    @Test
    fun `every time is ISO 8601 and parses`() {
        val json = export()
        Instant.parse(json.getString("exportedAt"))
        val shift = json.getJSONArray("shifts").getJSONObject(0)
        listOf("date", "clockIn", "clockOut", "modifiedAt").forEach { Instant.parse(shift.getString(it)) }
    }

    @Test
    fun `an empty app gives a valid file with empty lists`() {
        runBlocking { container.eraseAllData() }
        val json = export()
        assertEquals(0, json.getJSONArray("shifts").length())
        assertEquals(PersonalDataExport.SCHEMA_VERSION, json.getInt("schemaVersion"))
    }

    @Test
    fun `making the file is logged without its content`() {
        runBlocking { container.buildPersonalDataExport() }
        val entry = runBlocking { container.audit.awaitIdle(); container.audit.entries() }.first { it.action == AuditAction.EXPORT_PERSONAL_DATA }
        assertEquals(1, JSONObject(entry.metadata!!).getInt("shifts"))
        assertFalse(entry.metadata!!.contains("Dana"))
    }

    @Test
    fun `the button writes the file and opens the share sheet`() {
        fixture.show { PrivacySettingsSection(onOpenActivityLog = {}) }
        compose.onNodeWithText("Download my data").performClick()
        repeat(100) { if (shadowOf(app).peekNextStartedActivity() == null) { shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(30) } }
        val started = shadowOf(app).nextStartedActivity
        assertNotNull(started)
        assertEquals(Intent.ACTION_CHOOSER, started.action)
        val files = File(app.cacheDir, "exports").listFiles().orEmpty().map { it.name }
        assertTrue(files.any { it.startsWith("HoursTracker_my_data_") && it.endsWith(".json") })
    }
}
