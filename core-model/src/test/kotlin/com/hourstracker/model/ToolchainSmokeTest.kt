package com.hourstracker.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Verifies the machine running the tests, not the app: the JDK must ship the
 * Israeli daylight-saving rules and the locale week-start data the golden tests
 * rely on. If these fail, no golden result can be trusted.
 */
class ToolchainSmokeTest {
    private val jerusalem = ZoneId.of("Asia/Jerusalem")

    @Test
    fun `israel daylight saving starts on the Friday before the last Sunday of March`() {
        val transition = jerusalem.rules.nextTransition(Instant.parse("2026-01-01T00:00:00Z"))

        assertEquals(LocalDate.of(2026, 3, 27), transition.dateTimeBefore.toLocalDate())
        assertEquals(LocalTime.of(2, 0), transition.dateTimeBefore.toLocalTime())
        assertEquals(Duration.ofHours(1), transition.duration)
    }

    @Test
    fun `israel daylight saving ends on the last Sunday of October`() {
        val springForward = jerusalem.rules.nextTransition(Instant.parse("2026-01-01T00:00:00Z"))
        val fallBack = jerusalem.rules.nextTransition(springForward.instant)

        assertEquals(LocalDate.of(2026, 10, 25), fallBack.dateTimeBefore.toLocalDate())
        assertEquals(LocalTime.of(2, 0), fallBack.dateTimeBefore.toLocalTime())
        assertEquals(Duration.ofHours(-1), fallBack.duration)
    }

    @Test
    fun `week start follows the locale`() {
        val israel = WeekFields.of(Locale.forLanguageTag("he-IL"))
        val russia = WeekFields.of(Locale.forLanguageTag("ru-RU"))

        assertEquals(DayOfWeek.SUNDAY, israel.firstDayOfWeek)
        assertEquals(DayOfWeek.MONDAY, russia.firstDayOfWeek)
    }
}
