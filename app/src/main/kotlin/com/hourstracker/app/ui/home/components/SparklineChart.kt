package com.hourstracker.app.ui.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hourstracker.app.R
import com.hourstracker.app.ui.theme.Palette
import com.hourstracker.data.ShiftRecord
import com.hourstracker.model.IosCalendar
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * A simple sparkline chart showing daily hours for the last 7 days.
 * 
 * @param records List of completed shift records
 * @param calendar IosCalendar instance for date calculations
 * @param now Current instant for determining the date range
 * @param modifier Modifier to apply to the chart
 */
@Composable
fun SparklineChart(
    records: List<ShiftRecord>,
    calendar: IosCalendar,
    now: Instant,
    modifier: Modifier = Modifier
) {
    val dailyHours = calculateDailyHours(records, calendar, now)
    val maxHours = dailyHours.maxOrNull() ?: 0.0
    val description = stringResource(R.string.home_sparkline_a11y)

    // Capture theme colors before Canvas (which is not @Composable context)
    val accentColor = Palette.accent
    val textSecondaryColor = Palette.textSecondary

    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        if (dailyHours.isEmpty() || maxHours == 0.0) {
            // Draw empty state - just a horizontal line
            drawLine(
                color = textSecondaryColor.copy(alpha = 0.2f),
                start = Offset(x = 0f, y = size.height / 2f),
                end = Offset(x = size.width, y = size.height / 2f),
                strokeWidth = 1f
            )
            return@Canvas
        }

        val pointCount = dailyHours.size
        // Keep the line and its dots fully inside the canvas.
        val inset = 3.dp.toPx()
        val plotHeight = size.height - 2 * inset
        val plotWidth = size.width - 2 * inset
        val pointSpacing = if (pointCount > 1) plotWidth / (pointCount - 1) else 0f

        // Draw the line connecting points
        for (i in 0 until pointCount - 1) {
            val x1 = inset + i * pointSpacing
            val y1 = inset + plotHeight - (dailyHours[i] / maxHours).toFloat() * plotHeight
            val x2 = inset + (i + 1) * pointSpacing
            val y2 = inset + plotHeight - (dailyHours[i + 1] / maxHours).toFloat() * plotHeight

            drawLine(
                color = accentColor,
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 2.dp.toPx()
            )
        }

        // Draw points
        for (i in 0 until pointCount) {
            val x = inset + i * pointSpacing
            val y = inset + plotHeight - (dailyHours[i] / maxHours).toFloat() * plotHeight
            drawCircle(
                color = accentColor,
                radius = 2.5.dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
}

/**
 * Hours worked on each of the last 7 days, oldest first (index 6 is today). Finished shifts count their total;
 * a shift that is still running counts what it has earned so far, so the last point moves while you work.
 */
internal fun calculateDailyHours(
    records: List<ShiftRecord>,
    calendar: IosCalendar,
    now: Instant,
): List<Double> {
    val sessions = records.map { it.session }
    return (6 downTo 0).map { offset ->
        val day = calendar.startOfDay(now.minus(offset.toLong(), ChronoUnit.DAYS))
        sessions
            .filter { calendar.isSameDay(it.date, day) }
            .sumOf { session ->
                if (session.clockOut != null) {
                    session.totalHours
                } else {
                    val elapsed = (now.epochSecond - session.clockIn.epochSecond).coerceAtLeast(0L)
                    ((elapsed - session.recordedBreakSeconds(now)) / 3600.0).coerceAtLeast(0.0)
                }
            }
    }
}
