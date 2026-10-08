package com.hourstracker.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hourstracker.app.data.UserProfile
import com.hourstracker.app.screenshots.ScreenshotApp
import com.hourstracker.app.screenshots.ScreenshotFixture
import com.hourstracker.app.ui.settings.ActivityLogScreen
import com.hourstracker.data.AuditAction
import com.hourstracker.data.ShiftRecord
import com.hourstracker.model.WorkSession
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
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

/** Everything a person does to their data is logged, and nothing personal is written to the log. */
@RunWith(RobolectricTestRunner::class)
@Config(application = ScreenshotApp::class, sdk = [35])
class AuditLogUiTest {
    @get:Rule
    val compose = createComposeRule()
    private val fixture = ScreenshotFixture(compose)
    private val container get() = fixture.container
    private val id = UUID(9, 9)

    @Before
    fun setUp() {
        fixture.start()
        runBlocking { container.audit.awaitIdle() }
    }

    private fun shift(notes: String? = null, out: String = "16:00") = ShiftRecord(
        WorkSession(id, Instant.parse("2026-06-16T00:00:00Z"), Instant.parse("2026-06-16T08:00:00Z"), Instant.parse("2026-06-16T${out}:00Z")),
        notes = notes,
    )

    private fun entries(): List<com.hourstracker.data.db.AuditLogEntity> = runBlocking {
        container.audit.awaitIdle()
        container.audit.entries()
    }

    private fun lastOf(action: String) = entries().first { it.action == action }

    @Test
    fun `adding a shift logs a create with a hash and the source`() {
        runBlocking { container.shifts.upsert(shift()) }
        val e = lastOf(AuditAction.SHIFT_CREATE)
        assertEquals(id.toString(), e.targetId)
        assertEquals(64, e.afterHash!!.length)
        assertEquals(null, e.beforeHash)
        assertEquals("clock", JSONObject(e.metadata!!).getString("source"))
    }

    @Test
    fun `changing a shift logs an update that names the fields and carries both hashes`() {
        runBlocking {
            container.shifts.upsert(shift())
            container.shifts.upsert(shift(notes = "my private note", out = "17:30"))
        }
        val e = lastOf(AuditAction.SHIFT_UPDATE)
        val fields = JSONObject(e.metadata!!).getJSONArray("fields")
        assertEquals(listOf("clockOut", "notes"), (0 until fields.length()).map { fields.getString(it) })
        assertTrue(e.beforeHash != e.afterHash)
    }

    @Test
    fun `deleting a shift logs a delete`() {
        runBlocking {
            container.shifts.upsert(shift())
            container.shifts.delete(id)
        }
        val e = lastOf(AuditAction.SHIFT_DELETE)
        assertEquals(id.toString(), e.targetId)
        assertEquals(64, e.beforeHash!!.length)
    }

    @Test
    fun `no personal value ever reaches the log`() {
        runBlocking { container.shifts.upsert(shift(notes = "my private note")) }
        container.settings.save(
            container.settings.settings.value.copy(hourlyRate = 7733.5),
            UserProfile(fullName = "Secret Name", employeeNumber = "E-5551", workplaceName = "Secret Cafe"),
            idNumber = "123456782",
        )
        val text = entries().joinToString(" ") { listOf(it.actor, it.action, it.targetId, it.beforeHash, it.afterHash, it.metadata).joinToString(" ") }
        listOf("my private note", "Secret Name", "E-5551", "Secret Cafe", "123456782", "7733").forEach { assertFalse("log contains '$it'", text.contains(it)) }
    }

    @Test
    fun `a settings save logs the names of the changed fields, including the ID number as changed`() {
        container.settings.save(container.settings.settings.value.copy(hourlyRate = 61.0), UserProfile(fullName = "Dana L"), idNumber = "123456782")
        val e = lastOf(AuditAction.SETTINGS_UPDATE)
        val fields = JSONObject(e.metadata!!).getJSONArray("fields")
        val names = (0 until fields.length()).map { fields.getString(it) }
        assertTrue(names.containsAll(listOf("hourlyRate", "fullName", "idNumber")))
        assertEquals("pay_settings", e.targetId)
    }

    @Test
    fun `saving without any change logs nothing`() {
        val before = entries().size
        container.settings.save(container.settings.settings.value, container.settings.profile.value, idNumber = null)
        assertEquals(before, entries().size)
    }

    @Test
    fun `an app preference change is logged once and a repeated value is not`() {
        container.settings.saveShiftReminderEnabled(true)
        container.settings.saveShiftReminderEnabled(true)
        assertEquals(1, entries().count { it.action == AuditAction.SETTINGS_UPDATE && it.targetId == "app_preferences" && it.metadata!!.contains("shiftReminderEnabled") })
    }

    @Test
    fun `the Activity log screen lists entries and its filters narrow them`() {
        runBlocking { container.shifts.upsert(shift()) }
        container.settings.saveShiftSummaryEnabled(false)
        runBlocking { container.audit.awaitIdle() }
        fixture.show { ActivityLogScreen(onClose = {}) }
        fixture.awaitText("Shift added")
        compose.onAllNodesWithText("Settings changed")[0].assertExists()
        compose.onNodeWithText("Shifts").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Shift added").assertExists()
        compose.onNodeWithText("Settings changed").assertDoesNotExist()
    }

    @Test
    fun `an empty log shows its message`() {
        runBlocking { container.audit.awaitIdle() }
        fixture.show { ActivityLogScreen(onClose = {}) }
        // start() saved the profile once, so filter to a group that has nothing.
        fixture.awaitText("Settings changed")
        compose.onNodeWithText("Exports").performClick()
        compose.onNodeWithText("Nothing logged yet").assertExists()
    }
}
