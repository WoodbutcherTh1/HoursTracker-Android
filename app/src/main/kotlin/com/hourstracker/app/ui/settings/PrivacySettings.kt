package com.hourstracker.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hourstracker.app.LocalAppContainer
import com.hourstracker.app.R
import com.hourstracker.app.data.SettingsRepository
import com.hourstracker.app.ui.components.FormCard
import com.hourstracker.app.ui.components.FormRow
import com.hourstracker.app.ui.components.PickerRow
import com.hourstracker.app.ui.components.RowDivider
import com.hourstracker.app.ui.components.SectionHeader
import com.hourstracker.app.ui.theme.DsText
import com.hourstracker.app.ui.theme.Palette
import com.hourstracker.app.ui.theme.Space

/** What this phone remembers about its owner, and the controls over it. App choices: they apply at once, not with Save. */
@Composable
fun PrivacySettingsSection(onOpenActivityLog: () -> Unit) {
    val settings = LocalAppContainer.current.settings
    val container = LocalAppContainer.current
    val auditDays by settings.auditRetentionDays.collectAsState()
    val shiftYears by settings.shiftRetentionYears.collectAsState()
    val scope = rememberCoroutineScope()
    // A retention choice that would delete shifts right now waits for a yes.
    var pendingYears by remember { mutableStateOf<Int?>(null) }
    var pendingCount by remember { mutableIntStateOf(0) }

    SectionHeader(stringResource(R.string.settings_privacy_section))
    FormCard {
        FormRow(stringResource(R.string.settings_activity_log), modifier = Modifier.clickable(onClick = onOpenActivityLog)) {
            Text("›", style = DsText.titleSection, color = Palette.accent)
        }
        RowDivider()
        PickerRow(
            label = stringResource(R.string.settings_shift_retention),
            selected = shiftYears,
            options = SettingsRepository.SHIFT_RETENTION_CHOICES,
            optionLabel = { stringResource(shiftRetentionLabel(it)) },
            onSelect = { years ->
                scope.launch {
                    val affected = container.shiftsOlderThan(years)
                    if (affected == 0) {
                        settings.saveShiftRetentionYears(years)
                    } else {
                        pendingCount = affected
                        pendingYears = years
                    }
                }
            },
        )
        RowDivider()
        PickerRow(
            label = stringResource(R.string.settings_audit_retention),
            selected = auditDays,
            options = SettingsRepository.AUDIT_RETENTION_CHOICES,
            optionLabel = { stringResource(auditRetentionLabel(it)) },
            onSelect = settings::saveAuditRetentionDays,
        )
        Text(
            text = stringResource(R.string.settings_privacy_hint),
            style = DsText.meta,
            color = Palette.textSecondary,
            modifier = Modifier.padding(Space.md),
        )
    }

    pendingYears?.let { years ->
        AlertDialog(
            onDismissRequest = { pendingYears = null },
            title = { Text(stringResource(R.string.retention_confirm_title, stringResource(shiftRetentionLabel(years)), pendingCount)) },
            text = { Text(stringResource(R.string.retention_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingYears = null
                    settings.saveShiftRetentionYears(years)
                    scope.launch { container.applyShiftRetention() }
                }) { Text(stringResource(R.string.history_delete), color = Palette.overdue) }
            },
            dismissButton = { TextButton(onClick = { pendingYears = null }) { Text(stringResource(R.string.edit_cancel)) } },
        )
    }
}

private fun shiftRetentionLabel(years: Int): Int = when (years) {
    0 -> R.string.retention_never
    1 -> R.string.retention_1_year
    2 -> R.string.retention_2_years
    5 -> R.string.retention_5_years
    else -> R.string.retention_10_years
}

private fun auditRetentionLabel(days: Int): Int = when (days) {
    90 -> R.string.retention_3_months
    365 -> R.string.retention_1_year
    730 -> R.string.retention_2_years
    else -> R.string.retention_5_years
}
