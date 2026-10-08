package com.hourstracker.app.screenshots

import android.Manifest
import android.app.Application
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import com.hourstracker.app.HoursTrackerApp
import com.hourstracker.app.LocalAppContainer
import com.hourstracker.app.data.AppContainer
import com.hourstracker.app.data.MockShifts
import com.hourstracker.app.ui.theme.HoursTrackerTheme
import com.hourstracker.app.ui.theme.Palette
import com.hourstracker.data.ShiftRecord
import com.hourstracker.model.BreakInterval
import com.hourstracker.model.IosCalendar
import com.hourstracker.model.WorkSession
import kotlinx.coroutines.runBlocking
import org.robolectric.Shadows.shadowOf
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * Shared set-up for the screenshot tests: a fixed clock (Wednesday 17 June 2026, 10:30 UTC) and fixed shifts, so a
 * screen renders the same pixels on every run and on every machine.
 */
/** The app for screenshot tests: the container runs on [FIXED_CALENDAR] and never seeds mock shifts. */
class ScreenshotApp : HoursTrackerApp() {
    override fun createContainer() = AppContainer(this, calendarFactory = { ScreenshotFixture.FIXED_CALENDAR }, seedMockShifts = false)
}

class ScreenshotFixture(private val compose: ComposeContentTestRule) {
    val calendar = FIXED_CALENDAR

    companion object {
        private val ZONE: ZoneId = ZoneId.of("UTC")
        val FIXED_CALENDAR = IosCalendar(ZONE, 1, 1, Clock.fixed(Instant.parse("2026-06-17T10:30:00Z"), ZONE))
    }

    private val app get() = ApplicationProvider.getApplicationContext<Application>() as ScreenshotApp
    val container: AppContainer get() = app.container

    fun start() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val settings = container.settings.settings.value.copy(hourlyRate = 55.0)
        container.settings.save(settings, container.settings.profile.value.copy(fullName = "Dana Levi"), null)
    }

    /** About three months of plausible, finished shifts ending yesterday. */
    fun seedHistory() = runBlocking { MockShifts.build(calendar).forEach { container.shifts.upsert(it) } }

    /** A shift that started at 08:00 today and is still running, optionally with a break open since 10:00. */
    fun seedRunningShift(onBreak: Boolean) = runBlocking {
        val clockIn = Instant.parse("2026-06-17T08:00:00Z")
        val breaks = if (onBreak) listOf(BreakInterval(Instant.parse("2026-06-17T10:00:00Z"), null)) else emptyList()
        container.shifts.upsert(ShiftRecord(WorkSession(UUID(1, 1), calendar.startOfDay(clockIn), clockIn, null, 0, breaks), modifiedAt = clockIn))
    }

    fun show(content: @Composable () -> Unit) {
        compose.setContent {
            HoursTrackerTheme {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    Box(Modifier.fillMaxSize().background(Palette.background)) { content() }
                }
            }
        }
    }

    /** Lets the main looper run until [text] is on screen: Room and the view models hand results back through it. */
    fun awaitText(text: String, substring: Boolean = false) {
        repeat(200) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
            if (compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(25)
        }
        throw AssertionError("\"$text\" never appeared")
    }
}
