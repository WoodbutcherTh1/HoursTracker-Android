package com.hourstracker.app.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import com.hourstracker.app.HoursTrackerApp
import com.hourstracker.app.LocalAppContainer
import com.hourstracker.app.data.AppLanguage
import com.hourstracker.app.ui.nav.AppRoot
import com.hourstracker.app.ui.theme.HoursTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The five-tab shell: which tab is selected, where the tabs sit, and that Hebrew mirrors the layout. */
@RunWith(RobolectricTestRunner::class)
@Config(application = HoursTrackerApp::class, sdk = [35])
class NavigationUiTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(language: AppLanguage = AppLanguage.System, onDirection: (LayoutDirection) -> Unit = {}) {
        val app = ApplicationProvider.getApplicationContext<Application>() as HoursTrackerApp
        compose.setContent {
            HoursTrackerTheme {
                CompositionLocalProvider(LocalAppContainer provides app.container) {
                    onDirection(LocalLayoutDirection.current)
                    AppRoot(language = language, onLanguageChange = {})
                }
            }
        }
    }

    private fun tab(label: String) = compose.onNode(hasText(label) and isSelectable())

    @Test
    fun `home is selected at the start`() {
        show()
        tab("Home").assertIsSelected()
    }

    @Test
    fun `tapping a tab selects it and shows its screen`() {
        show()
        compose.onNode(hasText("History") and isSelectable()).performClick()
        tab("History").assertIsSelected()
        // The tab label and the screen title both read "History".
        assertEquals(2, compose.onAllNodesWithText("History").fetchSemanticsNodes().size)
        tab("Settings").performClick()
        tab("Settings").assertIsSelected()
    }

    @Test
    fun `going back to Home keeps the tab bar and selects Home`() {
        show()
        tab("Export").performClick()
        tab("Home").performClick()
        tab("Home").assertIsSelected()
        // The tab label and the Home screen title both read "Home".
        assertEquals(2, compose.onAllNodesWithText("Home").fetchSemanticsNodes().size)
    }

    @Test
    fun `in English the tabs run left to right`() {
        var direction = LayoutDirection.Rtl
        show { direction = it }
        compose.waitForIdle()
        assertEquals(LayoutDirection.Ltr, direction)
        val home = tab("Home").getUnclippedBoundsInRoot()
        val settings = tab("Settings").getUnclippedBoundsInRoot()
        assertTrue("Home should be left of Settings", home.left < settings.left)
    }

    @Test
    @Config(qualifiers = "iw")
    fun `in Hebrew the layout is mirrored and the tabs run right to left`() {
        var direction = LayoutDirection.Ltr
        show { direction = it }
        compose.waitForIdle()
        assertEquals(LayoutDirection.Rtl, direction)
        val home = tab("בית").getUnclippedBoundsInRoot()
        val settings = tab("הגדרות").getUnclippedBoundsInRoot()
        assertTrue("Home should be right of Settings", home.left > settings.left)
    }
}
