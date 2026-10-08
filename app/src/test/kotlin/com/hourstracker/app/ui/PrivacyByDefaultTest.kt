package com.hourstracker.app.ui

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.NotificationCompat
import com.hourstracker.app.screenshots.ScreenshotApp
import com.hourstracker.app.screenshots.ScreenshotFixture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * Privacy by default, enforced. These fail when someone adds a network, location, camera, analytics or crash-reporting
 * capability, or turns on a data-sharing default. A failure is a prompt to update `docs/COMPLIANCE_AUDIT.md`, the privacy
 * policy and `store/DATA_SAFETY.md` first, and only then this list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = ScreenshotApp::class, sdk = [35])
class PrivacyByDefaultTest {
    @get:Rule
    val compose = createComposeRule()
    private val fixture = ScreenshotFixture(compose)
    private val app get() = fixture.app

    @Before
    fun setUp() = fixture.start()

    private fun requested(): Set<String> =
        app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()

    // The module directory is the working directory of unit tests.
    private fun read(path: String) = File(path).readText()

    @Test
    fun `the app asks for no network, location, camera, microphone, contacts or advertising permission`() {
        val forbidden = listOf(
            Manifest.permission.INTERNET, Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION, Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS, "com.google.android.gms.permission.AD_ID",
            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
        assertEquals(emptyList<String>(), forbidden.filter { it in requested() })
    }

    @Test
    fun `only the permissions the audit lists are requested`() {
        val allowed = setOf(
            Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.RECEIVE_BOOT_COMPLETED, Manifest.permission.FOREGROUND_SERVICE,
            "android.permission.FOREGROUND_SERVICE_SPECIAL_USE", "${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        )
        assertEquals(emptySet<String>(), requested() - allowed)
    }

    @Test
    fun `Android backup is off`() {
        assertEquals(0, app.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun `cloud backup and device transfer rules exclude every kind of app data`() {
        val domains = listOf("root", "file", "database", "sharedpref", "external")
        listOf("src/main/res/xml/data_extraction_rules.xml", "src/main/res/xml/backup_rules.xml").forEach { path ->
            val text = read(path)
            domains.forEach { assertTrue("$path does not exclude $it", text.contains("<exclude domain=\"$it\"")) }
        }
    }

    @Test
    fun `no analytics, advertising, crash-reporting or networking library is a dependency`() {
        val catalog = read("../gradle/libs.versions.toml").lowercase()
        val build = (read("build.gradle.kts") + read("../core-data/build.gradle.kts") + read("../core-model/build.gradle.kts")).lowercase()
        val forbidden = listOf(
            "firebase", "crashlytics", "analytics", "sentry", "bugsnag", "mixpanel", "amplitude", "appsflyer", "adjust", "facebook", "applovin",
            "play-services", "datadog", "okhttp", "retrofit", "ktor", "volley", "supabase", "gemini",
        )
        val hits = forbidden.filter { catalog.contains(it) || build.contains(it) }
        assertEquals("a library that talks to a server needs the audit, policy and Data Safety form updated first: $hits", emptyList<String>(), hits)
    }

    @Test
    fun `nothing installs a crash handler or opens a network connection in code`() {
        val sources = File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.map { it.readText() }.joinToString("\n")
        listOf("setDefaultUncaughtExceptionHandler", "HttpURLConnection", "java.net.URL(", "java.net.Socket", "WebView").forEach {
            assertFalse("found $it", sources.contains(it))
        }
    }

    @Test
    fun `a fresh install shares nothing and starts the optional features off`() {
        val settings = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
        assertFalse(settings.getBoolean("shiftReminderEnabled", false))
        assertEquals(0, settings.getInt("shiftRetentionYears", 0))
        // Auto-deletion is off, the log is kept for a year, and there is no ID number until the owner types one.
        assertEquals("", fixture.container.settings.readIdNumber())
        assertEquals(365, fixture.container.settings.auditRetentionDays.value)
        assertEquals(0, fixture.container.settings.shiftRetentionYears.value)
    }

    @Test
    fun `the personal data file and the share provider are not exposed to other apps`() {
        val provider = app.packageManager.getProviderInfo(android.content.ComponentName(app.packageName, "androidx.core.content.FileProvider"), 0)
        assertFalse(provider.exported)
        assertTrue(provider.grantUriPermissions)
    }

    @Test
    fun `the shift summary shows no hours or pay on a locked screen`() {
        runBlocking {
            fixture.container.controller.clockIn()
            fixture.container.controller.clockOut()
        }
        val posted = shadowOf(app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).allNotifications
            .first { it.extras.getString("android.title") == "Shift complete" }
        assertEquals(NotificationCompat.VISIBILITY_PRIVATE, posted.visibility)
        val publicVersion = posted.publicVersion
        assertNotNull(publicVersion)
        assertEquals(null, publicVersion.extras.getCharSequence("android.text"))
    }
}
