package com.hourstracker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hourstracker.app.ui.theme.DsText
import com.hourstracker.app.ui.theme.Palette
import com.hourstracker.app.ui.theme.Radius
import com.hourstracker.app.ui.theme.Space
import com.hourstracker.app.ui.theme.dsCard
import com.hourstracker.model.PayFormatter
import java.util.Locale

/** One hours bucket of the tier bar: 100% / 125% / 150%. */
class PayTierSegment(val hours: Double, val color: Color)

class PayCardRow(val label: String, val value: String, val dot: Color? = null)

/**
 * The pay card: one hero amount, the hours split into pay tiers, and a couple of detail rows.
 * Shared by the onboarding preview and the Day Summary so the reward looks the same everywhere.
 */
@Composable
fun PayCard(
    amount: Double,
    currencyCode: String,
    caption: String,
    segments: List<PayTierSegment>,
    rows: List<PayCardRow>,
    modifier: Modifier = Modifier,
    note: String? = null,
    title: String? = null,
) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    Column(
        modifier = modifier.fillMaxWidth().dsCard(Radius.xl).padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        if (title != null) Text(text = title, style = DsText.meta, color = Palette.textTertiary)
        Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
            // Money is always laid out left to right so digits never reorder inside Hebrew or Arabic text.
            Text(
                text = PayFormatter.string(amount, currencyCode, locale),
                style = DsText.numHero,
                color = Palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            Text(text = caption, style = DsText.meta, color = Palette.textTertiary)
            if (note != null) Text(text = note, style = DsText.meta, color = Palette.textTertiary)
        }
        TierBar(segments)
        rows.forEach { row ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                if (row.dot != null) Box(Modifier.size(6.dp).background(row.dot, CircleShape))
                Text(text = row.label, style = DsText.sub, color = Palette.textSecondary, modifier = Modifier.weight(1f))
                Text(text = row.value, style = DsText.sub.copy(fontFeatureSettings = "tnum"), color = Palette.textPrimary)
            }
        }
    }
}

@Composable
private fun TierBar(segments: List<PayTierSegment>) {
    val visible = segments.filter { it.hours > 0 }
    Row(
        modifier = Modifier.fillMaxWidth().height(10.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (visible.isEmpty()) {
            Box(Modifier.weight(1f).height(10.dp).background(Palette.raised, CircleShape))
        } else {
            visible.forEach { segment ->
                Box(Modifier.weight(segment.hours.toFloat()).height(10.dp).background(segment.color, CircleShape))
            }
        }
    }
}
