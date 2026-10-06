package com.hourstracker.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Type scale (sp, so it follows the user's font size). Floors: 14sp content, 12sp metadata.
 * Numbers use tabular figures so digits do not jump while a timer runs, and are always laid out
 * left to right by the screens that show them.
 */
object DsText {
    private const val TABULAR = "tnum"

    val numHero = TextStyle(fontFamily = FontFamily.Default, fontSize = 48.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR)
    val numLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR)
    val titleScreen = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold)
    val titleSection = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    val headline = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal)
    val callout = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal)
    val sub = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal)
    val meta = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal)
}

/** Material typography mapped onto the scale, so stock components pick up the same sizes. */
internal val AppTypography = Typography(
    headlineLarge = DsText.titleScreen,
    titleLarge = DsText.titleSection,
    titleMedium = DsText.headline,
    bodyLarge = DsText.body,
    bodyMedium = DsText.callout,
    bodySmall = DsText.sub,
    labelSmall = DsText.meta,
)
