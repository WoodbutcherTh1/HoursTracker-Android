package com.hourstracker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hourstracker.app.ui.theme.DsText
import com.hourstracker.app.ui.theme.Palette
import com.hourstracker.app.ui.theme.Radius
import com.hourstracker.app.ui.theme.Space
import com.hourstracker.app.ui.theme.dsCard

/** A section title: a small accent icon and the title in secondary text above a card. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier = modifier.padding(start = Space.xs, end = Space.xs, top = Space.lg, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = Palette.accent, modifier = Modifier.size(16.dp))
        Text(text = title, style = DsText.sub, color = Palette.textSecondary)
    }
}

/** A card holding rows separated by hairlines. */
@Composable
fun FormCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier.fillMaxWidth().dsCard(Radius.lg)) { content() }
}

@Composable
fun RowDivider() {
    HorizontalDivider(color = Palette.hairline, thickness = 1.dp, modifier = Modifier.padding(horizontal = Space.md))
}

/** A label on the start side and a control on the end side. */
@Composable
fun FormRow(label: String, modifier: Modifier = Modifier, control: @Composable () -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).padding(horizontal = Space.md, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = DsText.body, color = Palette.textPrimary, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(Space.sm))
        control()
    }
}

/** A single-line text field filling a row. */
@Composable
fun TextRow(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = fieldColors(),
        shape = RoundedCornerShape(Radius.md),
        modifier = modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.xs),
    )
}

/** A label with a short numeric field (and an optional unit such as the currency symbol) on the end side. */
@Composable
fun NumberRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    unit: String? = null,
) {
    FormRow(label = label, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = DsText.body.copy(textAlign = TextAlign.End),
                colors = fieldColors(),
                shape = RoundedCornerShape(Radius.md),
                modifier = Modifier.width(112.dp),
            )
            if (unit != null) Text(text = unit, style = DsText.body, color = Palette.textSecondary)
        }
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    FormRow(label = label, modifier = modifier) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.ink,
                checkedTrackColor = Palette.accent,
                uncheckedThumbColor = Palette.textSecondary,
                uncheckedTrackColor = Palette.raised,
            ),
        )
    }
}

/** A label with the current choice; tapping opens a menu of [options]. */
@Composable
fun <T> PickerRow(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    FormRow(label = label, modifier = modifier.clickable { open = true }) {
        Text(text = optionLabel(selected), style = DsText.body, color = Palette.accent)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/** A label with minus and plus buttons around the current value. */
@Composable
fun StepperRow(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    minusDescription: String,
    plusDescription: String,
    modifier: Modifier = Modifier,
    step: Int = 1,
) {
    FormRow(label = label, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            StepperButton("−", minusDescription, enabled = value - step >= range.first) { onValueChange(value - step) }
            Text(text = value.toString(), style = DsText.body.copy(fontFeatureSettings = "tnum"), color = Palette.textPrimary)
            StepperButton("+", plusDescription, enabled = value + step <= range.last) { onValueChange(value + step) }
        }
    }
}

@Composable
private fun StepperButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(36.dp)
            .background(Palette.raised, CircleShape)
            .clickable(enabled = enabled, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = symbol, style = DsText.headline, color = if (enabled) Palette.accent else Palette.textTertiary)
    }
}

/** The primary call to action: accent fill, ink text, 56dp capsule. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    val alpha = if (enabled || loading) 1f else 0.35f
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Palette.accent.copy(alpha = alpha), CircleShape)
            .clickable(enabled = enabled && !loading, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.xs)) {
                androidx.compose.material3.CircularProgressIndicator(color = Palette.ink, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Text(text = text, style = DsText.headline, color = Palette.ink)
            }
        } else {
            Text(text = text, style = DsText.headline, color = Palette.ink.copy(alpha = if (enabled) 1f else 0.5f))
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Palette.textPrimary,
    unfocusedTextColor = Palette.textPrimary,
    focusedBorderColor = Palette.accent,
    unfocusedBorderColor = Palette.hairline,
    focusedLabelColor = Palette.accent,
    unfocusedLabelColor = Palette.textSecondary,
    cursorColor = Palette.accent,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
)
