package com.hourstracker.app.ui

import android.Manifest
import android.app.Application
import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.hourstracker.app.HoursTrackerApp
import com.hourstracker.app.LocalAppContainer
import com.hourstracker.app.data.AppContainer
import com.hourstracker.app.domain.ShiftClock
import com.hourstracker.app.ui.home.HomeScreen
import com.hourstracker.app.ui.theme.HoursTrackerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Clocking in and out on Home, end to end through the repository and the Room database. */
@RunWith(RobolectricTestRunner::class)
@Config(application = HoursTrackerApp::class, sdk = [35])
class HomeUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<Application>() as HoursTrackerApp
    private lateinit var testContainer: AppContainer

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        testContainer = AppContainer(app)
        app.container = testContainer
    }

    private fun show() {
        compose.setContent {
            HoursTrackerTheme {
                CompositionLocalProvider(LocalAppContainer provides testContainer) { HomeScreen() }
            }
        }
    }

    private fun running() = runBlocking { ShiftClock.active(testContainer.shifts.shifts.first()) }

    /** Waits for [condition], letting the main looper run: the view model and Room hand results back through it. */
    private fun awaitUntil(condition: () -> Boolean) {
        repeat(200) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
            if (condition()) return
            Thread.sleep(25)
        }
        throw AssertionError("condition not met in time")
    }

    private fun awaitText(text: String, substring: Boolean = false, ignoreCase: Boolean = false) =
        awaitUntil { compose.onAllNodesWithText(text, substring = substring, ignoreCase = ignoreCase).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `clocked out Home offers the door, clocking in starts a shift and shows the timer`() {
        show()
        compose.onNodeWithText("Clock In").assertIsDisplayed()
        compose.onNodeWithText("Clock In").performClick()
        awaitUntil { running() != null }
        awaitText("Clock Out")
        compose.onNodeWithText("Clock Out").assertExists()
        compose.onNodeWithText("00:00:0", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a break can be started and ended`() {
        show()
        compose.onNodeWithText("Clock In").performClick()
        awaitUntil { running() != null }
        awaitText("Going on break")
        compose.onNodeWithText("Going on break").performScrollTo().performClick()
        awaitUntil { running()?.session?.isOnBreak == true }
        compose.waitForIdle()
        assertNotNull(running()!!.session.activeBreak)
        awaitText("I'm back")
        compose.onNodeWithText("I'm back").performScrollTo().performClick()
        awaitUntil { running()?.session?.isOnBreak == false }
        compose.waitForIdle()
        assertNull(running()!!.session.activeBreak)
    }

    @Test
    fun `clocking out closes the shift and shows the summary`() {
        show()
        compose.onNodeWithText("Clock In").performClick()
        awaitUntil { running() != null }
        awaitText("Clock Out")
        compose.onNodeWithText("Clock Out").performScrollTo().performClick()
        awaitUntil { running() == null }
        awaitText("Shift complete")
        compose.onNodeWithText("Shift complete").assertIsDisplayed()
    }
}