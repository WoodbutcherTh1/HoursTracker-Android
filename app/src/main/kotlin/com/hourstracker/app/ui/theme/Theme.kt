package com.hourstracker.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

private val DarkScheme = darkColorScheme(
    primary = DarkPalette.accent,
    onPrimary = DarkPalette.ink,
    background = DarkPalette.background,
    onBackground = DarkPalette.textPrimary,
    surface = DarkPalette.card,
    onSurface = DarkPalette.textPrimary,
    surfaceVariant = DarkPalette.raised,
    onSurfaceVariant = DarkPalette.textSecondary,
    outline = DarkPalette.hairline,
    error = DarkPalette.overdue,
)

private val LightScheme = lightColorScheme(
    primary = LightPalette.accent,
    onPrimary = LightPalette.ink,
    background = LightPalette.background,
    onBackground = LightPalette.textPrimary,
    surface = LightPalette.card,
    onSurface = LightPalette.textPrimary,
    surfaceVariant = LightPalette.raised,
    onSurfaceVariant = LightPalette.textSecondary,
    outline = LightPalette.hairline,
    error = LightPalette.overdue,
)

/** Current theme colors (dark or light). */
data class ThemeColors(
    val background: androidx.compose.ui.graphics.Color,
    val card: androidx.compose.ui.graphics.Color,
    val raised: androidx.compose.ui.graphics.Color,
    val hairline: androidx.compose.ui.graphics.Color,
    val textPrimary: androidx.compose.ui.graphics.Color,
    val textSecondary: androidx.compose.ui.graphics.Color,
    val textTertiary: androidx.compose.ui.graphics.Color,
    val accent: androidx.compose.ui.graphics.Color,
    val ink: androidx.compose.ui.graphics.Color,
    val clockedIn: androidx.compose.ui.graphics.Color,
    val onBreak: androidx.compose.ui.graphics.Color,
    val overdue: androidx.compose.ui.graphics.Color,
    val success: androidx.compose.ui.graphics.Color,
    val warning: androidx.compose.ui.graphics.Color,
    val info: androidx.compose.ui.graphics.Color,
    val ot125: androidx.compose.ui.graphics.Color,
    val ot150: androidx.compose.ui.graphics.Color,
)

private val DarkColors = ThemeColors(
    background = DarkPalette.background,
    card = DarkPalette.card,
    raised = DarkPalette.raised,
    hairline = DarkPalette.hairline,
    textPrimary = DarkPalette.textPrimary,
    textSecondary = DarkPalette.textSecondary,
    textTertiary = DarkPalette.textTertiary,
    accent = DarkPalette.accent,
    ink = DarkPalette.ink,
    clockedIn = DarkPalette.clockedIn,
    onBreak = DarkPalette.onBreak,
    overdue = DarkPalette.overdue,
    success = DarkPalette.success,
    warning = DarkPalette.warning,
    info = DarkPalette.info,
    ot125 = DarkPalette.ot125,
    ot150 = DarkPalette.ot150,
)

private val LightColors = ThemeColors(
    background = LightPalette.background,
    card = LightPalette.card,
    raised = LightPalette.raised,
    hairline = LightPalette.hairline,
    textPrimary = LightPalette.textPrimary,
    textSecondary = LightPalette.textSecondary,
    textTertiary = LightPalette.textTertiary,
    accent = LightPalette.accent,
    ink = LightPalette.ink,
    clockedIn = LightPalette.clockedIn,
    onBreak = LightPalette.onBreak,
    overdue = LightPalette.overdue,
    success = LightPalette.success,
    warning = LightPalette.warning,
    info = LightPalette.info,
    ot125 = LightPalette.ot125,
    ot150 = LightPalette.ot150,
)

val LocalThemeColors = staticCompositionLocalOf { DarkColors }

/** Theme picker: Light/Dark/Auto. Android-specific addition (iOS is dark-only). */
@Composable
fun HoursTrackerTheme(themeMode: ThemeMode = ThemeMode.AUTO, content: @Composable () -> Unit) {
    val systemInDarkMode = isSystemInDarkTheme()
    val useDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AUTO -> systemInDarkMode
    }

    val colorScheme = if (useDarkTheme) DarkScheme else LightScheme
    val themeColors = if (useDarkTheme) DarkColors else LightColors

    CompositionLocalProvider(LocalThemeColors provides themeColors) {
        MaterialTheme(colorScheme = colorScheme, typography = AppTypography, content = content)
    }
}

/** `elev.1`: card surface plus a hairline. */
@Composable
fun Modifier.dsCard(radius: Dp = Radius.lg, fill: androidx.compose.ui.graphics.Color = Palette.card): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .background(fill, shape)
        .border(width = Dp.Hairline, color = Palette.hairline, shape = shape)
}
