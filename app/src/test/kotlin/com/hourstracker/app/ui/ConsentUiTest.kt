package com.hourstracker.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.hourstracker.app.BuildConfig
import com.hourstracker.app.data.AppFlags
import com.hourstracker.app.data.AppLanguage
import com.hourstracker.app.screenshots.ScreenshotApp
import com.hourstracker.app.screenshots.ScreenshotFixture
import com.hourstracker.app.ui.nav.AppGate
import com.hourstracker.data.AuditAction
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** Consent is versioned, timestamped, logged, and asked for again whenever the terms change. */
@RunWith(RobolectricTestRunner::class)
@Config(application = ScreenshotApp::class, sdk = [35])
class ConsentUiTest {
    @get:Rule
    val compose = createComposeRule()
    private val fixture = ScreenshotFixture(compose)
    private val container get() = fixture.container

    @Before
    fun setUp() {
        fixture.start()
    }

    private fun lines() = runBlocking { container.audit.awaitIdle(); container.audit.entries() }.filter { it.action == AuditAction.CONSENT_ACCEPTED }

    @Test
    fun `before anyone accepts there is no record and consent is needed`() {
        assertNull(container.flags.consentRecord())
        assertTrue(AppFlags.needsConsent(container.flags.acceptedLegalVersion.value))
    }

    @Test
    fun `the rule compares versions`() {
        assertTrue(AppFlags.needsConsent(0, 1))
        assertTrue(AppFlags.needsConsent(1, 2))
        assertFalse(AppFlags.needsConsent(2, 2))
        assertFalse(AppFlags.needsConsent(3, 2))
    }

    @Test
    fun `accepting stores the version, the time and the app version`() {
        container.acceptLegal()
        val record = container.flags.consentRecord()!!
        assertEquals(AppFlags.CURRENT_LEGAL_VERSION, record.version)
        assertEquals(Instant.parse("2026-06-17T10:30:00Z"), record.acceptedAt)
        assertEquals(BuildConfig.VERSION_NAME, record.appVersion)
    }

    @Test
    fun `every acceptance is logged with the version and whether it was a repeat`() {
        container.acceptLegal(version = 1)
        container.acceptLegal(version = 2)
        val logged = lines().map { JSONObject(it.metadata!!) }
        assertEquals(2, logged.size)
        // Newest first.
        assertEquals(2, logged[0].getInt("version"))
        assertTrue(logged[0].getBoolean("reconsent"))
        assertEquals(1, logged[1].getInt("version"))
        assertFalse(logged[1].getBoolean("reconsent"))
    }

    @Test
    fun `the log line holds no address and no device identifier`() {
        container.acceptLegal()
        val keys = JSONObject(lines().single().metadata!!).keys().asSequence().toSet()
        assertEquals(setOf("version", "appVersion", "reconsent"), keys)
    }

    @Test
    fun `a fresh install is asked, an old acceptance is asked again, and an up-to-date one is not`() {
        fixture.show { AppGate(container, onLanguageChange = {}) }
        compose.onNodeWithText("Before you start").assertExists()
    }

    @Test
    fun `when the terms version rises the consent screen comes back with the updated title`() {
        container.acceptLegal(version = 1)
        fixture.show { AppGate(container, onLanguageChange = {}, currentLegalVersion = 2) }
        compose.onNodeWithText("We've updated our terms").assertExists()
        compose.onNodeWithText("I have read and agree to the Terms of Use and the Privacy Policy").performScrollTo().performClick()
        compose.onNodeWithText("Agree and continue").performClick()
        compose.waitForIdle()
        assertEquals(2, container.flags.acceptedLegalVersion.value)
        val newest = JSONObject(lines().first().metadata!!)
        assertEquals(2, newest.getInt("version"))
        assertTrue(newest.getBoolean("reconsent"))
        assertNotNull(container.flags.consentRecord()!!.acceptedAt)
    }

    @Test
    fun `an up-to-date acceptance goes straight on to onboarding`() {
        container.acceptLegal()
        fixture.show { AppGate(container, onLanguageChange = {}) }
        compose.onNodeWithText("Before you start").assertDoesNotExist()
    }

    @Test
    fun `the supported languages for the consent screen are unchanged`() {
        assertEquals(5, AppLanguage.entries.size)
    }
}
