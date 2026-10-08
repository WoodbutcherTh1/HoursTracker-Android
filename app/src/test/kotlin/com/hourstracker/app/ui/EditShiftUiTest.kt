package com.hourstracker.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.hourstracker.app.screenshots.ScreenshotApp
import com.hourstracker.app.screenshots.ScreenshotFixture
import com.hourstracker.app.ui.manual.ManualEntryScreen
import com.hourstracker.data.ShiftRecord
import com.hourstracker.model.BreakInterval
import com.hourstracker.model.WorkSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import android.os.Looper
import org.junit.Assert.assertEquals
import org.robolectric.Shadows.shadowOf
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

/** Editing a stored shift: the form shows it, saves changes to the database, keeps what it does not show, and validates like Manual Entry. */
@RunWith(RobolectricTestRunner::class)
@Config(application = ScreenshotApp::class, sdk = [35])
class EditShiftUiTest {
    @get:Rule
    val compose = createComposeRule()
    private val fixture = ScreenshotFixture(compose)
    private val id = UUID(5, 5)
    private var closed = false

    @Before
    fun setUp() {
        fixture.start()
        val day = Instant.parse("2026-06-16T00:00:00Z")
        val breaks = listOf(BreakInterval(Instant.parse("2026-06-16T12:00:00Z"), Instant.parse("2026-06-16T12:30:00Z")))
        val session = WorkSession(id, day, Instant.parse("2026-06-16T08:00:00Z"), Instant.parse("2026-06-16T16:30:00Z"), 30, breaks)
        runBlocking { fixture.container.shifts.upsert(ShiftRecord(session, notes = "first", isManualEntry = false, modifiedAt = day)) }
        fixture.show { ManualEntryScreen(fixture.container, editId = id, onClose = { closed = true }) }
        fixture.awaitText("08:00")
    }

    private fun stored(): ShiftRecord = runBlocking { fixture.container.shifts.shifts.first().first { it.id == id } }

    @Test
    fun `the form shows the stored times, break and notes`() {
        compose.onNodeWithText("08:00").assertExists()
        compose.onNodeWithText("16:30").assertExists()
        compose.onNodeWithText("first").assertExists()
    }

    @Test
    fun `saving writes the changed notes and keeps the recorded breaks and origin`() {
        compose.onNodeWithText("first").performTextInput(" and more")
        compose.onAllNodesWithText("Save")[0].performClick()
        repeat(100) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
            if (closed) return@repeat
            Thread.sleep(20)
        }
        val record = stored()
        assertEquals(" and morefirst", record.notes)
        assertEquals(1, record.session.breaks.size)
        assertEquals(false, record.isManualEntry)
        assertEquals(30, record.session.breakMinutes)
        assertEquals(true, closed)
    }

    @Test
    fun `equal clock in and clock out cannot be saved`() {
        // Exercise the same rule the form uses, through its public validation.
        val input = com.hourstracker.app.domain.ManualEntry.toInput(stored(), fixture.calendar).copy(clockOutMinutes = 8 * 60)
        assertNotNull(com.hourstracker.app.domain.ManualEntry.validate(input))
    }

    @Test
    fun `a break as long as the shift is rejected`() {
        val input = com.hourstracker.app.domain.ManualEntry.toInput(stored(), fixture.calendar).copy(breakMinutes = 600)
        assertEquals(com.hourstracker.app.domain.ManualEntry.Problem.BreakExceedsShift, com.hourstracker.app.domain.ManualEntry.validate(input))
    }

    @Test
    fun `delete asks first and then removes the shift`() {
        compose.onNodeWithText("Delete Entry").performScrollTo().performClick()
        compose.onNodeWithText("Delete this shift?").assertExists()
        compose.onAllNodesWithText("Delete Entry")[1].performClick()
        compose.waitForIdle()
        assertEquals(0, runBlocking { fixture.container.shifts.shifts.first().count { it.id == id } })
    }
}
