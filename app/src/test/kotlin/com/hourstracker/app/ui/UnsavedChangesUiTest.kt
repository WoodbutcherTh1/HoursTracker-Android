package com.hourstracker.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.hourstracker.app.data.AppLanguage
import com.hourstracker.app.screenshots.ScreenshotApp
import com.hourstracker.app.screenshots.ScreenshotFixture
import com.hourstracker.app.ui.nav.AppRoot
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings keeps edits in a draft; leaving the tab with unsaved edits asks what to do. Nothing is stored before Save. */
@RunWith(RobolectricTestRunner::class)
@Config(application = ScreenshotApp::class, sdk = [35])
class UnsavedChangesUiTest {
    @get:Rule
    val compose = createComposeRule()
    private val fixture = ScreenshotFixture(compose)

    @Before
    fun setUp() {
        fixture.start()
        fixture.show { AppRoot(AppLanguage.English, onLanguageChange = {}) }
        compose.onNode(hasText("Settings") and isSelectable()).performClick()
        fixture.awaitText("Worker Information")
    }

    private fun editName() = compose.onNodeWithText("Dana Levi").performTextInput("X")

    private fun storedName() = fixture.container.settings.profile.value.fullName

    @Test
    fun `an edit is not stored until Save`() {
        editName()
        compose.waitForIdle()
        assertEquals("Dana Levi", storedName())
    }

    @Test
    fun `leaving Settings with unsaved edits shows the question`() {
        editName()
        compose.onNode(hasText("History") and isSelectable()).performClick()
        compose.onNodeWithText("You have unsaved changes. Save them?").assertIsDisplayed()
    }

    @Test
    fun `Save in the question stores the edit and leaves`() {
        editName()
        compose.onNode(hasText("History") and isSelectable()).performClick()
        compose.onNode(hasText("Save") and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.isDialog())).performClick()
        compose.waitForIdle()
        assertEquals("XDana Levi", storedName())
        compose.onNode(hasText("History") and isSelectable()).assertIsDisplayed()
    }

    @Test
    fun `Discard drops the edit and leaves`() {
        editName()
        compose.onNode(hasText("History") and isSelectable()).performClick()
        compose.onNodeWithText("Discard").performClick()
        compose.waitForIdle()
        assertEquals("Dana Levi", storedName())
    }

    @Test
    fun `leaving Settings with nothing changed asks nothing`() {
        compose.onNode(hasText("History") and isSelectable()).performClick()
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithTextCount("You have unsaved changes. Save them?"))
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String) =
        onAllNodes(hasText(text)).fetchSemanticsNodes().size
}
