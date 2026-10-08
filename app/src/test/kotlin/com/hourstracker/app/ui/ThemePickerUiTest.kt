package com.hourstracker.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.hourstracker.app.data.AppLanguage
import com.hourstracker.app.screenshots.ScreenshotApp
import com.hourstracker.app.screenshots.ScreenshotFixture
import com.hourstracker.app.ui.settings.SettingsScreen
import com.hourstracker.app.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = ScreenshotApp::class, sdk = [35])
class ThemePickerUiTest {
    @get:Rule
    val compose = createComposeRule()
    private val fixture = ScreenshotFixture(compose)

    @Before
    fun setUp() {
        fixture.start()
        fixture.show { SettingsScreen(AppLanguage.English, onLanguageChange = {}) }
        fixture.awaitText("Worker Information")
    }

    @Test
    fun `the theme row starts on Auto and lists Light, Dark and Auto`() {
        compose.onNodeWithText("Auto (like the phone) ▾").performScrollTo().performClick()
        compose.onNodeWithText("Light").assertExists()
        compose.onNodeWithText("Dark").assertExists()
    }

    @Test
    fun `choosing Dark is stored at once and survives a restart`() {
        compose.onNodeWithText("Auto (like the phone) ▾").performScrollTo().performClick()
        compose.onNodeWithText("Dark").performClick()
        assertEquals(ThemeMode.DARK, fixture.container.settings.themeMode.value)
        compose.onNodeWithText("Dark ▾").assertExists()
    }

    @Test
    fun `choosing Light is stored`() {
        compose.onNodeWithText("Auto (like the phone) ▾").performScrollTo().performClick()
        compose.onNodeWithText("Light").performClick()
        assertEquals(ThemeMode.LIGHT, fixture.container.settings.themeMode.value)
    }
}
