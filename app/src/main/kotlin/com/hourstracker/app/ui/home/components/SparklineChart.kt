package com.hourstracker.app.ui.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hourstracker.app.ui.theme.Palette
import com.hourstracker.app.ui.theme.Space
import com.hourstracker.model.HistoryPeriodHelper
import com.hourstracker.data.ShiftRecord
import com.hourstracker.model.IosCalendar
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

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
    val chartHeight = 48.dp
    val chartWidth = 120.dp
    
    Canvas(
        modifier = modifier
            .size(chartWidth, chartHeight)
    ) {
        if (dailyHours.isEmpty() || maxHours == 0.0) {
            // Draw empty state - just a horizontal line
            drawLine(
                color = Palette.textSecondary.copy(alpha = 0.2f),
                start = Offset(x = 0f, y = size.height / 2f),
                end = Offset(x = size.width, y = size.height / 2f),
                strokeWidth = 1f
            )
            return@Canvas
        }
        
        val pointCount = dailyHours.size
        val pointSpacing = if (pointCount > 1) size.width / (pointCount - 1) else 0f
        
        // Draw the line connecting points
        for (i in 0 until pointCount - 1) {
            val x1 = i * pointSpacing
            val ratio1 = if (maxHours > 0.0) (dailyHours[i] / maxHours).toFloat() else 0f
            val y1 = size.height - ratio1 * size.height
            val x2 = (i + 1) * pointSpacing
            val ratio2 = if (maxHours > 0.0) (dailyHours[i + 1] / maxHours).toFloat() else 0f
            val y2 = size.height - ratio2 * size.height
            
            drawLine(
                color = Palette.accent,
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 2f
            )
        }
        
        // Draw points
        for (i in 0 until pointCount) {
            val x = i * pointSpacing
            val ratio = if (maxHours > 0.0) (dailyHours[i] / maxHours).toFloat() else 0f
            val y = size.height - ratio * size.height
            drawCircle(
                color = Palette.accent,
                radius = 2f,
                center = Offset(x, y)
            )
        }
    }
}

/**
 * Calculates the hours for each of the last 7 days.
 * 
 * @param records List of completed shift records
 * @param calendar IosCalendar instance for date calculations
 * @param now Current instant for determining the date range
 * @return List of hours for each of the last 7 days (most recent first)
 */
private fun calculateDailyHours(
    records: List<ShiftRecord>,
    calendar: IosCalendar,
    now: Instant
): List<Double> {
    val completed = records.map { it.session }.filter { it.clockOut != null }
    
    // Calculate the last 7 days (including today)
    val dailyHours = DoubleArray(7) { 0.0 }
    
    for (offset in 0..6) {
        val targetDate = calendar.startOfDay(now.minus(offset.toLong(), ChronoUnit.DAYS))
        val hoursForDay = completed
            .filter { calendar.isSameDay(it.date, targetDate) }
            .fold(0.0) { sum, session -> sum + session.totalHours }
        dailyHours[6 - offset] = hoursForDay // Store in array so index 0 is 6 days ago, index 6 is today
    }
    
    return dailyHours.toList()
}

/**
 * Extension to convert DoubleArray to List
 */
private fun <T> Array<T>.toList(): List<T> {
    val list = java.util.ArrayList<T>(size)
    for (item in this) list.add(item)
    return list
}
