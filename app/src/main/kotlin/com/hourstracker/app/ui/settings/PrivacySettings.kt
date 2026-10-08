package com.hourstracker.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
    val auditDays by settings.auditRetentionDays.collectAsState()

    SectionHeader(stringResource(R.string.settings_privacy_section))
    FormCard {
        FormRow(stringResource(R.string.settings_activity_log), modifier = Modifier.clickable(onClick = onOpenActivityLog)) {
            Text("›", style = DsText.titleSection, color = Palette.accent)
        }
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
}

private fun auditRetentionLabel(days: Int): Int = when (days) {
    90 -> R.string.retention_3_months
    365 -> R.string.retention_1_year
    730 -> R.string.retention_2_years
    else -> R.string.retention_5_years
}
