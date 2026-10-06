package com.hourstracker.app.ui.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hourstracker.app.R
import com.hourstracker.app.data.AppLanguage
import com.hourstracker.app.ui.components.PickerRow
import com.hourstracker.app.ui.components.PrimaryButton
import com.hourstracker.app.ui.theme.DsText
import com.hourstracker.app.ui.theme.Palette
import com.hourstracker.app.ui.theme.Radius
import com.hourstracker.app.ui.theme.Space
import com.hourstracker.app.ui.theme.dsCard

/**
 * The agreement to the Terms of Use and Privacy Policy. The app cannot be used behind it. Consent is an
 * explicit, unticked checkbox plus a button; it is never pre-checked.
 */
@Composable
fun LegalConsentScreen(
    isReconsent: Boolean,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onAccept: () -> Unit,
) {
    var agreed by rememberSaveable { mutableStateOf(false) }
    var showDeclined by rememberSaveable { mutableStateOf(false) }
    var reading by rememberSaveable { mutableStateOf<String?>(null) }

    reading?.let { name ->
        LegalTextScreen(document = LegalDocument.valueOf(name), onBack = { reading = null })
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(Palette.background).statusBarsPadding().navigationBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.xs), horizontalArrangement = Arrangement.End) {
            PickerRow(
                label = "",
                selected = language,
                options = AppLanguage.entries,
                optionLabel = { it.label ?: stringResource(R.string.settings_language_system) },
                onSelect = onLanguageChange,
                modifier = Modifier.size(width = 200.dp, height = 40.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            Text(
                text = stringResource(if (isReconsent) R.string.legal_gate_title_updated else R.string.legal_gate_title),
                style = DsText.titleScreen,
                color = Palette.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = Space.lg),
            )
            Text(text = stringResource(R.string.legal_gate_body), style = DsText.body, color = Palette.textSecondary, textAlign = TextAlign.Center)

            Column(modifier = Modifier.fillMaxWidth().dsCard(Radius.md).padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                listOf(R.string.legal_gate_point_estimates, R.string.legal_gate_point_data, R.string.legal_gate_point_control).forEach {
                    Text(text = stringResource(it), style = DsText.callout, color = Palette.textPrimary)
                }
            }

            Column(modifier = Modifier.fillMaxWidth().dsCard(Radius.md)) {
                Text(
                    text = stringResource(R.string.terms_title),
                    style = DsText.body,
                    color = Palette.accent,
                    modifier = Modifier.fillMaxWidth().clickable { reading = LegalDocument.Terms.name }.padding(Space.md),
                )
                Text(
                    text = stringResource(R.string.privacy_title),
                    style = DsText.body,
                    color = Palette.accent,
                    modifier = Modifier.fillMaxWidth().clickable { reading = LegalDocument.Privacy.name }.padding(Space.md),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { agreed = !agreed }
                    .semantics { role = Role.Checkbox; selected = agreed },
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.Top,
            ) {
                Text(text = if (agreed) "☑" else "☐", style = DsText.titleSection, color = if (agreed) Palette.accent else Palette.textSecondary)
                Text(text = stringResource(R.string.legal_gate_checkbox), style = DsText.sub, color = Palette.textPrimary)
            }
        }
        Column(modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            PrimaryButton(text = stringResource(R.string.legal_gate_accept), onClick = onAccept, enabled = agreed)
            Text(
                text = stringResource(R.string.legal_gate_decline),
                style = DsText.sub,
                color = Palette.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().background(Palette.background, RoundedCornerShape(Radius.md)).clickable { showDeclined = true }.padding(Space.sm),
            )
        }
    }

    if (showDeclined) {
        AlertDialog(
            onDismissRequest = { showDeclined = false },
            title = { Text(stringResource(R.string.legal_gate_decline_title)) },
            text = { Text(stringResource(R.string.legal_gate_decline_message)) },
            confirmButton = { TextButton(onClick = { showDeclined = false }) { Text(stringResource(R.string.legal_gate_read_again)) } },
        )
    }
}
