package com.hourstracker.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
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
import android.widget.Toast
import com.hourstracker.app.ui.export.FileSharing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
fun PrivacySettingsSection(onOpenActivityLog: () -> Unit, onRestart: () -> Unit = { }) {
    val settings = LocalAppContainer.current.settings
    val container = LocalAppContainer.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val auditDays by settings.auditRetentionDays.collectAsState()
    val shiftYears by settings.shiftRetentionYears.collectAsState()
    val scope = rememberCoroutineScope()
    // A retention choice that would delete shifts right now waits for a yes.
    var pendingYears by remember { mutableStateOf<Int?>(null) }
    var pendingCount by remember { mutableIntStateOf(0) }
    var confirmErase by remember { mutableStateOf(false) }
    var eraseFailed by remember { mutableStateOf<List<String>?>(null) }
    var erasing by remember { mutableStateOf(false) }

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
        RowDivider()
        FormRow(stringResource(R.string.settings_download_data), modifier = Modifier.clickable {
            scope.launch {
                try {
                    val text = container.buildPersonalDataExport()
                    val stamp = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm", java.util.Locale.ROOT).format(container.deviceCalendar().now().atZone(container.deviceCalendar().zone))
                    val file = withContext(Dispatchers.IO) {
                        FileSharing.cacheFile(context, "HoursTracker_my_data_$stamp.json").also { it.writeText(text, Charsets.UTF_8) }
                    }
                    FileSharing.share(context, file, "application/json")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("PersonalData", "Download failed", e)
                    Toast.makeText(context, R.string.download_failed, Toast.LENGTH_LONG).show()
                }
            }
        }) {
            Text("›", style = DsText.titleSection, color = Palette.accent)
        }
        RowDivider()
        FormRow(stringResource(R.string.settings_erase), modifier = Modifier.clickable { confirmErase = true }) {
            Text(if (erasing) "…" else "›", style = DsText.titleSection, color = Palette.overdue)
        }
        Text(
            text = stringResource(R.string.settings_privacy_hint),
            style = DsText.meta,
            color = Palette.textSecondary,
            modifier = Modifier.padding(Space.md),
        )
    }

    if (confirmErase) {
        EraseDialog(
            onDismiss = { confirmErase = false },
            onConfirm = {
                confirmErase = false
                erasing = true
                scope.launch {
                    val failed = container.eraseAllData()
                    erasing = false
                    if (failed.isEmpty()) onRestart() else eraseFailed = failed
                }
            },
        )
    }
    eraseFailed?.let { failed ->
        AlertDialog(
            onDismissRequest = { eraseFailed = null },
            text = { Text(stringResource(R.string.erase_failed, failed.joinToString(", "))) },
            confirmButton = { TextButton(onClick = { eraseFailed = null }) { Text(stringResource(R.string.error_ok)) } },
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

/** The last chance: the person has to type the confirmation word before the red button works. */
@Composable
private fun EraseDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val word = stringResource(R.string.erase_word)
    var typed by remember { mutableStateOf("") }
    val matches = typed.trim().equals(word, ignoreCase = true) || typed.trim().equals("DELETE", ignoreCase = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.erase_title)) },
        text = {
            Column {
                Text(stringResource(R.string.erase_body), style = DsText.sub)
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.erase_type_prompt, word)) },
                    modifier = Modifier.fillMaxWidth().padding(top = Space.md),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = matches) { Text(stringResource(R.string.erase_confirm), color = if (matches) Palette.overdue else Palette.textTertiary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.edit_cancel)) } },
    )
}
