package com.hourstracker.app.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hourstracker.app.ui.theme.DsText
import com.hourstracker.app.ui.theme.Palette
import com.hourstracker.app.ui.theme.Space

/**
 * The floating pill tab bar: translucent card, the selected tab sits on a raised pill and takes the accent.
 * Row order follows the layout direction, so it mirrors in Hebrew and Arabic.
 */
@Composable
fun FloatingTabBar(selected: TopLevelTab, onSelect: (TopLevelTab) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(32.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .background(Palette.card.copy(alpha = 0.92f), shape)
            .border(width = 1.dp, color = Palette.hairline, shape = shape)
            .padding(Space.xxs),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopLevelTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val tint = if (isSelected) Palette.accent else Palette.textPrimary
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (isSelected) Palette.raised else androidx.compose.ui.graphics.Color.Transparent)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) })
                    .padding(vertical = Space.xxs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(imageVector = tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                Text(
                    text = stringResource(tab.label),
                    style = DsText.meta,
                    color = tint,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
