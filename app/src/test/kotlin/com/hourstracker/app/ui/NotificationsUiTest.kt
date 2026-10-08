package com.hourstracker.app.ui

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.hourstracker.app.data.ShiftReminderReceiver
import com.hourstracker.app.screenshots.ScreenshotApp
import com.hourstracker.app.screenshots.ScreenshotFixture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The three reminders: shift start, break end (switches and timing) and the summary after clocking out. */
@RunWith(RobolectricTestRunner::class)
@Config(application = ScreenshotApp::class, sdk = [35])
class NotificationsUiTest {
    @get:Rule
    val compose = createComposeRule()
    private val fixture = ScreenshotFixture(compose)
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val notifications get() = shadowOf(app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
    private val alarms get() = shadowOf(app.getSystemService(Context.ALARM_SERVICE) as AlarmManager)

    @Before
    fun setUp() = fixture.start()

    private fun settle() = repeat(60) {
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(20)
    }

    private fun titles() = notifications.allNotifications.map { it.extras.getString("android.title") }

    @Test
    fun `the shift reminder is off by default and arms no alarm`() {
        settle()
        assertNull(alarms.peekNextScheduledAlarm())
    }

    @Test
    fun `turning the shift reminder on arms an alarm and turning it off clears it`() {
        val settings = fixture.container.settings
        settings.saveShiftReminderEnabled(true)
        settle()
        assertNotNull(alarms.peekNextScheduledAlarm())
        settings.saveShiftReminderEnabled(false)
        settle()
        assertNull(alarms.peekNextScheduledAlarm())
    }

    @Test
    fun `the lead time is kept between 5 and 120 minutes`() {
        val settings = fixture.container.settings
        settings.saveShiftReminderMinutesBefore(1)
        assertEquals(5, settings.shiftReminderMinutesBefore.value)
        settings.saveShiftReminderMinutesBefore(500)
        assertEquals(120, settings.shiftReminderMinutesBefore.value)
    }

    @Test
    fun `when the alarm fires the reminder shows`() {
        fixture.container.settings.saveShiftReminderEnabled(true)
        runBlocking { ShiftReminderReceiver.handle(app, fixture.container) }
        assertEquals(listOf("Your shift starts soon"), titles())
    }

    @Test
    fun `no reminder while a shift is already running`() {
        fixture.container.settings.saveShiftReminderEnabled(true)
        fixture.seedRunningShift(onBreak = false)
        runBlocking { ShiftReminderReceiver.handle(app, fixture.container) }
        assertEquals(emptyList<String?>(), titles())
    }

    @Test
    fun `no reminder when it was turned off before the alarm fired`() {
        runBlocking { ShiftReminderReceiver.handle(app, fixture.container) }
        assertEquals(emptyList<String?>(), titles())
    }

    @Test
    fun `clocking out from the notification posts the summary`() {
        runBlocking {
            fixture.container.controller.clockIn()
            fixture.container.controller.clockOut()
        }
        assertEquals(true, titles().contains("Shift complete"))
    }

    @Test
    fun `clocking out from Home posts no summary because Home shows its own`() {
        runBlocking {
            fixture.container.controller.clockIn()
            fixture.container.controller.clockOut(notifySummary = false)
        }
        assertEquals(false, titles().contains("Shift complete"))
    }

    @Test
    fun `the summary switch turns the notification off`() {
        fixture.container.settings.saveShiftSummaryEnabled(false)
        runBlocking {
            fixture.container.controller.clockIn()
            fixture.container.controller.clockOut()
        }
        assertEquals(false, titles().contains("Shift complete"))
    }
}
