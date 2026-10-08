package com.hourstracker.app.screenshots

import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import android.os.Looper
import com.github.takahirom.roborazzi.captureRoboImage
import com.hourstracker.app.data.AppLanguage
import com.hourstracker.app.ui.nav.AppRoot
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The five Google Play phone screenshots in one language: 1080 x 1920 (360 x 640 dp at 3x), dark theme, the whole app
 * with its tab bar. Run with `./gradlew :app:recordRoborazziDebug -PplayScreenshots`; the pictures land in
 * `store/graphics/screenshots/<language>/`. Without the flag these tests are skipped.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class PlayScreenshotsBase(private val lang: String) {
    @get:Rule
    val compose = createComposeRule()
    private val fixture = ScreenshotFixture(compose)

    @Before
    fun setUp() {
        assumeTrue("Play screenshots run only with -PplayScreenshots", System.getProperty("playScreenshots") != null)
        fixture.start()
        fixture.seedHistory()
    }

    private fun settle() = repeat(80) {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
        Thread.sleep(25)
    }

    private fun openTab(index: Int) {
        compose.onAllNodes(isSelectable())[index].performClick()
        settle()
    }

    private fun capture(name: String) = compose.onRoot().captureRoboImage("../store/graphics/screenshots/$lang/$name.png")

    private fun showApp() {
        fixture.show { AppRoot(language = AppLanguage.System, onLanguageChange = {}) }
        settle()
    }

    @Test
    fun s1HomeClockedOut() {
        showApp()
        capture("1_home_clocked_out")
    }

    @Test
    fun s2HomeClockedIn() {
        fixture.seedRunningShift(onBreak = false)
        showApp()
        capture("2_home_clocked_in")
    }

    @Test
    fun s3History() {
        showApp()
        openTab(1)
        capture("3_history")
    }

    @Test
    fun s4Export() {
        showApp()
        openTab(3)
        capture("4_export")
    }

    @Test
    fun s5Settings() {
        showApp()
        openTab(4)
        capture("5_settings")
    }
}

@Config(application = ScreenshotApp::class, sdk = [35], qualifiers = "en-rUS-w360dp-h640dp-night-xxhdpi")
class PlayScreenshotsEn : PlayScreenshotsBase("en")

@Config(application = ScreenshotApp::class, sdk = [35], qualifiers = "he-rIL-w360dp-h640dp-night-xxhdpi")
class PlayScreenshotsHe : PlayScreenshotsBase("he")

@Config(application = ScreenshotApp::class, sdk = [35], qualifiers = "ar-rIL-w360dp-h640dp-night-xxhdpi")
class PlayScreenshotsAr : PlayScreenshotsBase("ar")

@Config(application = ScreenshotApp::class, sdk = [35], qualifiers = "ru-rRU-w360dp-h640dp-night-xxhdpi")
class PlayScreenshotsRu : PlayScreenshotsBase("ru")
