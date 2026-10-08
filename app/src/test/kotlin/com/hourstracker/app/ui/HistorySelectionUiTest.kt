package com.hourstracker.app.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.hourstracker.app.screenshots.ScreenshotApp
import com.hourstracker.app.screenshots.ScreenshotFixture
import com.hourstracker.app.ui.history.HistoryScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Long-press a shift to start selecting, tap to add or remove, delete the selection after a confirmation. */
@RunWith(RobolectricTestRunner::class)
@Config(application = ScreenshotApp::class, sdk = [35], qualifiers = "w360dp-h740dp-xhdpi")
class HistorySelectionUiTest {
    @get:Rule
    val compose = createComposeRule()
    private val fixture = ScreenshotFixture(compose)
    private var edited = 0

    @Before
    fun setUp() {
        fixture.start()
        fixture.seedHistory()
        fixture.show { HistoryScreen(onAdd = {}, onEdit = { edited++ }) }
        fixture.awaitText("Amount")
    }

    private fun count() = runBlocking { fixture.container.shifts.shifts.first().size }

    private fun row(date: String) = compose.onNode(hasText(date, substring = true) and androidx.compose.ui.test.hasClickAction())

    @Test
    fun `a long press starts selecting and a tap on another row adds it`() {
        row("06/16").performTouchInput { longClick() }
        compose.onNodeWithText("Selected: 1").assertExists()
        row("06/15").performClick()
        compose.onNodeWithText("Selected: 2").assertExists()
        assertEquals(0, edited)
    }

    @Test
    fun `tapping a selected row unselects it and the last one ends selecting`() {
        row("06/16").performTouchInput { longClick() }
        row("06/16").performClick()
        compose.onNodeWithText("Delete selected").assertDoesNotExist()
        row("06/16").performClick()
        assertEquals(1, edited)
    }

    @Test
    fun `the cross cancels the selection without deleting`() {
        val before = count()
        row("06/16").performTouchInput { longClick() }
        compose.onNodeWithContentDescription("Cancel selection").performClick()
        compose.onNodeWithText("Selected: 1").assertDoesNotExist()
        assertEquals(before, count())
    }

    @Test
    fun `deleting asks first and then removes only the selected shifts`() {
        val before = count()
        row("06/16").performTouchInput { longClick() }
        row("06/15").performClick()
        compose.onNodeWithText("Delete selected").performClick()
        compose.onNodeWithText("Delete the selected shifts (2)?").assertExists()
        compose.onNode(hasText("Delete") and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.isDialog())).performClick()
        fixture.awaitText("Deleted shifts: 2")
        assertEquals(before - 2, count())
    }

    @Test
    fun `declining the confirmation keeps every shift`() {
        val before = count()
        row("06/16").performTouchInput { longClick() }
        compose.onNodeWithText("Delete selected").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(before, count())
    }
}
