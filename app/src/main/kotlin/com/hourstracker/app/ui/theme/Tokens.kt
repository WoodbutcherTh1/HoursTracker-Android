package com.hourstracker.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * "Calm Neon" design tokens, matching the iOS app: dark surfaces, depth from surface lightness plus a
 * hairline (no shadows), one static glow per screen behind the hero element.
 *
 * Contrast ratios are WCAG against [Palette.card].
 */
object Palette {
    /** The default background; the iOS app lets the user pick another. */
    val background = Color(0xFF0A0D0F)
    val card = Color(0xFF16191D)
    val raised = Color(0xFF1E2227)
    val hairline = Color.White.copy(alpha = 0.08f)

    val textPrimary = Color(0xFFF2F4F5) // 16.0:1
    val textSecondary = Color(0xFFA3ABB2) // 7.6:1
    val textTertiary = Color(0xFF7C858D) // 4.7:1, metadata only

    /** Default accent (the user may pick another on iOS). */
    val accent = Color(0xFF26F273)

    /** Text on an accent-filled button. */
    val ink = Color(0xFF06110B)

    // States: fixed, they carry meaning and never follow the accent.
    val clockedIn = Color(0xFFFF6B6B) // 6.4:1
    val onBreak = Color(0xFFFFB547) // 10.0:1
    val overdue = Color(0xFFFF453A) // 5.2:1
    val success = Color(0xFF34D399) // 9.2:1
    val warning = Color(0xFFFBBF24) // 10.6:1
    val info = Color(0xFF60A5FA) // 6.9:1

    // Pay tiers: warmer means more per hour.
    val ot125 = Color(0xFFF5B942) // 10.0:1
    val ot150 = Color(0xFFFF8A4C) // 7.6:1
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
