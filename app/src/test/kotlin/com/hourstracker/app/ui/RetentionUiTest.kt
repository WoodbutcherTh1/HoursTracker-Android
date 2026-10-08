package com.hourstracker.app.ui

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hourstracker.app.data.AppContainer
import com.hourstracker.app.data.IdCipher
import com.hourstracker.app.data.SettingsRepository
import com.hourstracker.app.screenshots.ScreenshotApp
import com.hourstracker.app.screenshots.ScreenshotFixture
import com.hourstracker.app.ui.settings.PrivacySettingsSection
import com.hourstracker.data.AuditAction
import com.hourstracker.data.ShiftRecord
import com.hourstracker.model.WorkSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

/** Automatic deletion of old shifts: off by default, exact about what is old, never touches a running shift, always logged. */
@RunWith(RobolectricTestRunner::class)
@Config(application = ScreenshotApp::class, sdk = [35])
class RetentionUiTest {
    @get:Rule
    val compose = createComposeRule()
    private val fixture = ScreenshotFixture(compose)
    private val container get() = fixture.container
    private var n = 0L

    // "Today" in the fixture is 17 June 2026.
    private fun shift(day: String, open: Boolean = false): ShiftRecord {
        val date = Instant.parse("${day}T00:00:00Z")
        val clockIn = date.plusSeconds(8 * 3600)
        return ShiftRecord(WorkSession(UUID(7, ++n), date, clockIn, if (open) null else clockIn.plusSeconds(8 * 3600)))
    }

    private fun ids(): Set<UUID> = runBlocking { container.shifts.shifts.first().map { it.id }.toSet() }

    @Before
    fun setUp() {
        fixture.start()
    }

    private fun seed(): List<ShiftRecord> {
        val records = listOf(shift("2014-03-01"), shift("2020-01-10"), shift("2024-06-16"), shift("2024-06-17"), shift("2026-06-10"), shift("2015-01-01", open = true))
        runBlocking { records.forEach { container.shifts.upsert(it) } }
        return records
    }

    @Test
    fun `the default is never and nothing is deleted`() {
        seed()
        assertEquals(0, container.settings.shiftRetentionYears.value)
        assertEquals(0, runBlocking { container.applyShiftRetention() })
        assertEquals(6, ids().size)
    }

    @Test
    fun `two years deletes shifts dated before the same day two years ago and keeps the rest`() {
        val records = seed()
        container.settings.saveShiftRetentionYears(2)
        // 17 June 2024 is exactly two years back: the shift ON that day stays, the day before goes.
        // 2014, 2020 and 2024-06-16 (one day too old) are old; the running 2015 shift is not counted.
        assertEquals(3, runBlocking { container.shiftsOlderThan(2) })
        assertEquals(3, runBlocking { container.applyShiftRetention() })
        assertEquals(setOf(records[3].id, records[4].id, records[5].id), ids())
    }

    @Test
    fun `a running shift is kept however old it is`() {
        val records = seed()
        container.settings.saveShiftRetentionYears(1)
        runBlocking { container.applyShiftRetention() }
        assertTrue(records[5].id in ids())
    }

    @Test
    fun `the cleanup is logged once with the count and no shift details`() {
        seed()
        container.settings.saveShiftRetentionYears(5)
        val removed = runBlocking { container.applyShiftRetention() }
        runBlocking { container.audit.awaitIdle() }
        val entry = runBlocking { container.audit.entries() }.single { it.action == AuditAction.RETENTION_CLEANUP }
        assertEquals("system", entry.actor)
        val json = JSONObject(entry.metadata!!)
        assertEquals(removed, json.getInt("removed"))
        assertEquals(5, json.getInt("olderThanYears"))
    }

    @Test
    fun `nothing old enough means nothing logged`() {
        runBlocking { container.shifts.upsert(shift("2026-06-10")) }
        container.settings.saveShiftRetentionYears(1)
        runBlocking { container.applyShiftRetention() }
        runBlocking { container.audit.awaitIdle() }
        assertTrue(runBlocking { container.audit.entries() }.none { it.action == AuditAction.RETENTION_CLEANUP })
    }

    @Test
    fun `the cleanup runs when the app starts`() {
        val records = seed()
        container.settings.saveShiftRetentionYears(1)
        val restarted = AppContainer(
            fixture.app, calendarFactory = { ScreenshotFixture.FIXED_CALENDAR }, seedMockShifts = false, idCipher = IdCipher { ScreenshotFixture.TEST_KEY },
        )
        repeat(100) { if (runBlocking { restarted.shifts.shifts.first().size } <= 2) return@repeat else Thread.sleep(30) }
        assertEquals(setOf(records[4].id, records[5].id), runBlocking { restarted.shifts.shifts.first().map { it.id }.toSet() })
    }

    @Test
    fun `only the offered choices are accepted`() {
        container.settings.saveShiftRetentionYears(3)
        assertEquals(0, container.settings.shiftRetentionYears.value)
        assertEquals(listOf(0, 1, 2, 5, 10), SettingsRepository.SHIFT_RETENTION_CHOICES)
    }

    @Test
    fun `choosing a period that would delete shifts asks first, and no keeps them`() {
        seed()
        fixture.show { PrivacySettingsSection(onOpenActivityLog = {}) }
        compose.onNodeWithText("Never ▾").performClick()
        compose.onNodeWithText("1 year").performClick()
        fixture.awaitText("Delete shifts older than 1 year", substring = true)
        compose.onNode(hasText("Cancel")).performClick()
        compose.waitForIdle()
        assertEquals(0, container.settings.shiftRetentionYears.value)
        assertEquals(6, ids().size)
    }

    @Test
    fun `confirming saves the choice and deletes now`() {
        seed()
        fixture.show { PrivacySettingsSection(onOpenActivityLog = {}) }
        compose.onNodeWithText("Never ▾").performClick()
        compose.onNodeWithText("1 year").performClick()
        fixture.awaitText("Delete shifts older than 1 year", substring = true)
        compose.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()
        repeat(100) { if (ids().size > 2) Thread.sleep(30) }
        assertEquals(1, container.settings.shiftRetentionYears.value)
        assertEquals(2, ids().size)
    }

    @Test
    fun `choosing a period when nothing is that old just saves it`() {
        runBlocking { container.shifts.upsert(shift("2026-06-10")) }
        fixture.show { PrivacySettingsSection(onOpenActivityLog = {}) }
        compose.onNodeWithText("Never ▾").performClick()
        compose.onNodeWithText("10 years").performClick()
        repeat(100) { if (container.settings.shiftRetentionYears.value != 10) Thread.sleep(30) }
        assertEquals(10, container.settings.shiftRetentionYears.value)
        assertEquals(1, ids().size)
    }
}
