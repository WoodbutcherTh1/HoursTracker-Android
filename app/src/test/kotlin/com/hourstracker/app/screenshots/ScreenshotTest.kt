package com.hourstracker.app.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.hourstracker.app.data.AppLanguage
import com.hourstracker.app.ui.export.ExportScreen
import com.hourstracker.app.ui.history.HistoryScreen
import com.hourstracker.app.ui.home.DaySummaryContent
import com.hourstracker.app.ui.home.DaySummary
import com.hourstracker.app.ui.home.HomeScreen
import com.hourstracker.app.ui.manual.ManualEntryScreen
import com.hourstracker.app.ui.onboarding.OnboardingScreen
import com.hourstracker.app.ui.settings.SettingsScreen
import com.hourstracker.model.OvertimeCalculator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Visual regression for the ten key screens. Record the pictures with `./gradlew :app:recordRoborazziDebug`,
 * compare with `:app:verifyRoborazziDebug`. A plain `test` run renders nothing, so these cost almost no time there.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ScreenshotApp::class, sdk = [35], qualifiers = "w360dp-h740dp-xhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()
    private val fixture = ScreenshotFixture(compose)

    @Before
    fun setUp() = fixture.start()

    private fun capture(name: String) = compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")

    @Test
    fun homeClockedOut() {
        fixture.show { HomeScreen() }
        fixture.awaitText("Clock In")
        capture("home_clocked_out")
    }

    @Test
    fun homeClockedIn() {
        fixture.seedRunningShift(onBreak = false)
        fixture.show { HomeScreen() }
        fixture.awaitText("Clock Out")
        capture("home_clocked_in")
    }

    @Test
    fun homeOnBreak() {
        fixture.seedRunningShift(onBreak = true)
        fixture.show { HomeScreen() }
        fixture.awaitText("I'm back")
        capture("home_on_break")
    }

    @Test
    fun historyEmpty() {
        fixture.show { HistoryScreen(onAdd = {}, onEdit = {}) }
        fixture.awaitText("No Entries Yet")
        capture("history_empty")
    }

    @Test
    fun historyPopulated() {
        fixture.seedHistory()
        fixture.show { HistoryScreen(onAdd = {}, onEdit = {}) }
        // The table header only exists once the shifts have loaded (until then History shows its skeleton).
        fixture.awaitText("Amount")
        capture("history_populated")
    }

    @Test
    fun export() {
        fixture.seedHistory()
        fixture.show { ExportScreen() }
        fixture.awaitText("Export preview")
        fixture.awaitText("Days")
        capture("export")
    }

    @Test
    fun settings() {
        fixture.show { SettingsScreen(AppLanguage.English, onLanguageChange = {}) }
        fixture.awaitText("Settings")
        capture("settings")
    }

    @Test
    fun manualEntry() {
        fixture.show { ManualEntryScreen(fixture.container, editId = null, onClose = {}) }
        fixture.awaitText("Save", substring = true)
        capture("manual_entry")
    }

    @Test
    fun onboardingLanguagePicker() {
        val settings = fixture.container.settings.settings.value
        fixture.show {
            OnboardingScreen(
                settings = settings,
                calendar = fixture.calendar,
                language = AppLanguage.English,
                onLanguageChange = {},
                onFinish = {},
            )
        }
        compose.waitForIdle()
        capture("onboarding_language")
    }

    @Test
    fun daySummarySheet() {
        fixture.seedHistory()
        val settings = fixture.container.settings.settings.value
        val sessions = runBlocking { fixture.container.shifts.shifts.first() }.map { it.session }
        val closed = sessions.maxBy { it.clockIn }
        val summary = DaySummary(OvertimeCalculator.breakdown(closed, sessions, settings, fixture.calendar), closed.breakMinutes)
        fixture.show { DaySummaryContent(summary, showNet = true, onShowNet = {}, onDismiss = {}) }
        fixture.awaitText("Shift complete")
        capture("day_summary")
    }
}
