package com.hourstracker.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * "Calm Neon" design tokens, matching the iOS app: dark surfaces, depth from surface lightness plus a
 * hairline (no shadows), one static glow per screen behind the hero element.
 *
 * Contrast ratios are WCAG against dark mode [card].
 */
object Palette {
    /** Access current theme colors (light or dark). */
    val background: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeColors.current.background

    val card: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeColors.current.card

    val raised: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeColors.current.raised

    val hairline: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeColors.current.hairline

    val textPrimary: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeColors.current.textPrimary

    val textSecondary: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeColors.current.textSecondary

    val textTertiary: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeColors.current.textTertiary

    val accent: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeColors.current.accent

    val ink: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeColors.current.ink

    val clockedIn: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeColors.current.clockedIn

    val onBreak: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeColors.current.onBreak

    val overdue: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeColors.current.overdue

    val success: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeColors.current.success

    val warning: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeColors.current.warning

    val info: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeColors.current.info

    val ot125: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeColors.current.ot125

    val ot150: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeColors.current.ot150
}

/** Dark mode colors - the original default. */
object DarkPalette {
    val background = Color(0xFF0A0D0F)
    val card = Color(0xFF16191D)
    val raised = Color(0xFF1E2227)
    val hairline = Color.White.copy(alpha = 0.08f)
    val textPrimary = Color(0xFFF2F4F5)
    val textSecondary = Color(0xFFA3ABB2)
    val textTertiary = Color(0xFF7C858D)
    val accent = Color(0xFF26F273)
    val ink = Color(0xFF06110B)
    val clockedIn = Color(0xFFFF6B6B)
    val onBreak = Color(0xFFFFB547)
    val overdue = Color(0xFFFF453A)
    val success = Color(0xFF34D399)
    val warning = Color(0xFFFBBF24)
    val info = Color(0xFF60A5FA)
    val ot125 = Color(0xFFF5B942)
    val ot150 = Color(0xFFFF8A4C)
}

/** Spacing, base 4. */
object Space {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

/** Corner radii. */
object Radius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

/** Light theme palette - Android-specific addition for theme picker. */
object LightPalette {
    val background = Color(0xFFF5F7FA)
    val card = Color(0xFFFFFFFF)
    val raised = Color(0xFFF0F2F5)
    val hairline = Color.Black.copy(alpha = 0.08f)

    val textPrimary = Color(0xFF0A0D0F)
    val textSecondary = Color(0xFF4A5568)
    val textTertiary = Color(0xFF718096)

    val accent = Color(0xFF10B981) // Green, lighter in light mode

    val ink = Color(0xFFFFFFFF)

    // States: adjusted for light mode
    val clockedIn = Color(0xFFDC2626)
    val onBreak = Color(0xFFF59E0B)
    val overdue = Color(0xFFEF4444)
    val success = Color(0xFF10B981)
    val warning = Color(0xFFF59E0B)
    val info = Color(0xFF3B82F6)

    // Pay tiers
    val ot125 = Color(0xFFD97706)
    val ot150 = Color(0xFFDC2626)
}
