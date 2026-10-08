package com.hourstracker.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class HistoryFilterUiTest {
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
    fun `the chips are there and Payroll is the default`() {
        listOf("Week", "Month", "Payroll", "Year", "All").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("June 2026").assertExists()
    }

    @Test
    fun `Year shows the year as the title`() {
        compose.onNodeWithText("Year").performClick()
        compose.onNodeWithText("2026").assertExists()
    }

    @Test
    fun `All shows every shift under one title`() {
        compose.onNodeWithText("All").performClick()
        compose.onNodeWithText("All shifts").assertExists()
        compose.onNodeWithContentDescription("Previous period").assertDoesNotExist()
    }
}
