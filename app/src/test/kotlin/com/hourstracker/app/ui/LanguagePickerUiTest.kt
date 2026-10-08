package com.hourstracker.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.hourstracker.app.data.AppLanguage
import com.hourstracker.app.screenshots.ScreenshotApp
import com.hourstracker.app.screenshots.ScreenshotFixture
import com.hourstracker.app.ui.settings.SettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The app-language row in Settings: it looks like a dropdown, lists every language, and reports the choice. */
@RunWith(RobolectricTestRunner::class)
@Config(application = ScreenshotApp::class, sdk = [35])
class LanguagePickerUiTest {
    @get:Rule
    val compose = createComposeRule()
    private val fixture = ScreenshotFixture(compose)
    private var chosen: AppLanguage? = null

    @Before
    fun setUp() {
        fixture.start()
        fixture.show { SettingsScreen(AppLanguage.System, onLanguageChange = { chosen = it }) }
        fixture.awaitText("Worker Information")
        compose.onNodeWithText("System ▾", substring = true).performScrollTo().performClick()
    }

    @Test
    fun `the row shows a chevron and opens a list with every language`() {
        listOf("עברית", "English", "العربية", "Русский").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        assertEquals(1, compose.onAllNodesWithText("System").fetchSemanticsNodes().size)
    }

    @Test
    fun `choosing a language reports it`() {
        compose.onNodeWithText("Русский").performClick()
        assertEquals(AppLanguage.Russian, chosen)
    }

    @Test
    fun `the picker lists Hebrew first and the system default last`() {
        assertEquals(listOf("Hebrew", "English", "Arabic", "Russian", "System"), AppLanguage.entries.map { it.name })
    }
}
