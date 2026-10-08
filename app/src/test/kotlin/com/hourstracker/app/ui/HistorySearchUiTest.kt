package com.hourstracker.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.hourstracker.app.screenshots.ScreenshotApp
import com.hourstracker.app.screenshots.ScreenshotFixture
import com.hourstracker.app.ui.history.HistoryScreen
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = ScreenshotApp::class, sdk = [35], qualifiers = "w360dp-h740dp-xhdpi")
class HistorySearchUiTest {
    @get:Rule
    val compose = createComposeRule()
    private val fixture = ScreenshotFixture(compose)

    @Before
    fun setUp() {
        fixture.start()
        fixture.seedHistory()
        fixture.show { HistoryScreen(onAdd = {}, onEdit = {}) }
        fixture.awaitText("Amount")
    }

    @Test
    fun `typing a date narrows the list`() {
        compose.onNodeWithText("Search by date or amount").performTextInput("06/12")
        compose.waitForIdle()
        compose.onNodeWithText("06/16").assertDoesNotExist()
        compose.onNodeWithText("Hours: 05:00").assertExists()
    }

    @Test
    fun `no match shows the empty message and clearing brings the list back`() {
        compose.onNodeWithText("Search by date or amount").performTextInput("zzz")
        compose.onNodeWithText("No matching shifts").assertExists()
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithText("06/16").assertExists()
    }
}
