package com.hourstracker.app.ui

import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.hourstracker.app.HoursTrackerApp
import com.hourstracker.app.screenshots.ScreenshotApp
import com.hourstracker.app.screenshots.ScreenshotFixture
import com.hourstracker.app.ui.components.EmptyState
import com.hourstracker.app.ui.components.ErrorState
import com.hourstracker.app.ui.components.HistorySkeleton
import com.hourstracker.app.ui.components.NoticeBanner
import com.hourstracker.app.ui.export.ExportScreen
import com.hourstracker.app.ui.history.HistoryScreen
import com.hourstracker.app.ui.home.HomeScreen
import com.hourstracker.app.ui.nav.TabIcons
import com.hourstracker.app.ui.payslips.PayslipsScreen
import com.hourstracker.app.ui.components.PrimaryButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Loading, empty and error states, and the accessibility labels added with them. */
@RunWith(RobolectricTestRunner::class)
@Config(application = ScreenshotApp::class, sdk = [35])
class StatesUiTest {
    @get:Rule
    val compose = createComposeRule()
    private val fixture = ScreenshotFixture(compose)

    @Before
    fun setUp() = fixture.start()

    @Test
    fun `an empty state shows its title and body, and its action works`() {
        var clicks = 0
        fixture.show { EmptyState(TabIcons.History, "Nothing here", "Add something", actionLabel = "Add", onAction = { clicks++ }) }
        compose.onNodeWithText("Nothing here").assertIsDisplayed()
        compose.onNodeWithText("Add something").assertIsDisplayed()
        compose.onNodeWithText("Add").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `an empty state without an action shows no button`() {
        fixture.show { EmptyState(TabIcons.Payslips, "Nothing here", "Add something") }
        compose.onNodeWithText("Nothing here").assertIsDisplayed()
        compose.onNodeWithText("Add").assertDoesNotExist()
    }

    @Test
    fun `an error state offers a retry`() {
        var retries = 0
        fixture.show { ErrorState(TabIcons.Export, "It failed", "Try later", actionLabel = "Try again", onAction = { retries++ }) }
        compose.onNodeWithText("It failed").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun `a notice banner runs its action`() {
        var opened = false
        fixture.show { NoticeBanner("Notifications are off", "Settings", onAction = { opened = true }) }
        compose.onNodeWithText("Settings").assertHasClickAction().performClick()
        assertTrue(opened)
    }

    @Test
    fun `a loading button shows its label and ignores taps`() {
        var clicks = 0
        fixture.show { PrimaryButton(text = "Creating…", onClick = { clicks++ }, loading = true) }
        compose.onNodeWithText("Creating…").assertIsDisplayed().performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun `the history skeleton announces that it is loading`() {
        fixture.show { HistorySkeleton() }
        compose.onNodeWithContentDescription("Loading").assertExists()
    }

    @Test
    fun `history with no shifts shows the first-time empty state with an add button`() {
        fixture.show { HistoryScreen(onAdd = {}, onEdit = {}) }
        fixture.awaitText("No Entries Yet")
        compose.onNodeWithText("Add a shift", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `history rows show no delete background at rest`() {
        fixture.seedHistory()
        fixture.show { HistoryScreen(onAdd = {}, onEdit = {}) }
        fixture.awaitText("Amount")
        compose.onNodeWithText("Delete").assertDoesNotExist()
    }

    @Test
    fun `history controls have spoken labels`() {
        fixture.show { HistoryScreen(onAdd = {}, onEdit = {}) }
        fixture.awaitText("No Entries Yet")
        compose.onNode(hasContentDescription("Add a shift")).assertHasClickAction()
        compose.onNode(hasContentDescription("Previous period")).assertHasClickAction()
        compose.onNode(hasContentDescription("Next period")).assertHasClickAction()
    }

    @Test
    fun `payslips shows its empty state`() {
        fixture.show { PayslipsScreen() }
        compose.onNodeWithText("No payslips yet").assertIsDisplayed()
    }

    @Test
    fun `export with no shifts shows the empty state and disables the button`() {
        fixture.show { ExportScreen() }
        compose.onNodeWithText("No shifts in this range").assertExists()
        assertEquals(0, compose.onAllNodesWithText("Creating…").fetchSemanticsNodes().size)
    }

    @Test
    fun `the export language option never shows a raw placeholder`() {
        fixture.show { ExportScreen() }
        assertEquals(0, compose.onAllNodesWithText("%1\$s", substring = true).fetchSemanticsNodes().size)
        compose.onNodeWithText("Phone language (English)").assertExists()
    }

    @Test
    fun `home warns while a shift runs and notifications are off`() {
        val app = ApplicationProvider.getApplicationContext<HoursTrackerApp>()
        shadowOf(app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).setNotificationsEnabled(false)
        fixture.seedRunningShift(onBreak = false)
        fixture.show { HomeScreen() }
        fixture.awaitText("Clock Out")
        compose.onNodeWithText("Notifications are off", substring = true).assertExists()
    }

    @Test
    fun `home shows no notification warning when they are on`() {
        fixture.seedRunningShift(onBreak = false)
        fixture.show { HomeScreen() }
        fixture.awaitText("Clock Out")
        compose.onNodeWithText("Notifications are off", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the clock in door has a spoken action`() {
        fixture.show { HomeScreen() }
        fixture.awaitText("Clock In")
        compose.onNodeWithText("Clock In").assertHasClickAction()
    }
}
