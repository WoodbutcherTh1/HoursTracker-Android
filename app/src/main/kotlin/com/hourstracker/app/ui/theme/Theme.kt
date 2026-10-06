package com.hourstracker.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

private val DarkScheme = darkColorScheme(
    primary = Palette.accent,
    onPrimary = Palette.ink,
    background = Palette.background,
    onBackground = Palette.textPrimary,
    surface = Palette.card,
    onSurface = Palette.textPrimary,
    surfaceVariant = Palette.raised,
    onSurfaceVariant = Palette.textSecondary,
    outline = Palette.hairline,
    error = Palette.overdue,
)

/** The app is dark only, as on iOS: there is no light theme to switch to. */
@Composable
fun HoursTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, typography = AppTypography, content = content)
}

/** `elev.1`: card surface plus a hairline. */
fun Modifier.dsCard(radius: Dp = Radius.lg, fill: androidx.compose.ui.graphics.Color = Palette.card): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .background(fill, shape)
        .border(width = Dp.Hairline, color = Palette.hairline, shape = shape)
}
