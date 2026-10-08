package com.hourstracker.app.ui.legal

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hourstracker.app.R
import com.hourstracker.app.ui.theme.DsText
import com.hourstracker.app.ui.theme.Palette
import com.hourstracker.app.ui.theme.Radius
import com.hourstracker.app.ui.theme.Space
import com.hourstracker.app.ui.theme.dsCard

enum class LegalDocument { Terms, Privacy }

private class Section(@StringRes val title: Int, @StringRes val body: Int)

private val termsSections = listOf(
    Section(R.string.terms_section_service, R.string.terms_body_service),
    Section(R.string.terms_section_estimates, R.string.terms_body_estimates),
    Section(R.string.terms_section_responsibility, R.string.terms_body_responsibility),
    Section(R.string.terms_section_account, R.string.terms_body_account),
    Section(R.string.terms_section_use, R.string.terms_body_use),
    Section(R.string.terms_section_liability, R.string.terms_body_liability),
    Section(R.string.terms_section_changes, R.string.terms_body_changes),
    Section(R.string.terms_section_law, R.string.terms_body_law),
    Section(R.string.terms_section_contact, R.string.terms_body_contact),
)

private val privacySections = listOf(
    Section(R.string.privacy_section_data, R.string.privacy_body_data),
    Section(R.string.privacy_section_account, R.string.privacy_body_account),
    Section(R.string.privacy_section_support, R.string.privacy_body_support),
    Section(R.string.privacy_section_tracking, R.string.privacy_body_tracking),
    Section(R.string.privacy_section_retention, R.string.privacy_body_retention),
    Section(R.string.privacy_section_rights, R.string.privacy_body_rights),
    Section(R.string.privacy_section_security, R.string.privacy_body_security),
    Section(R.string.privacy_section_controls, R.string.privacy_body_controls),
    Section(R.string.privacy_section_children, R.string.privacy_body_children),
    Section(R.string.privacy_section_changes, R.string.privacy_body_changes),
    Section(R.string.privacy_section_contact, R.string.privacy_body_contact),
)

/**
 * The in-app Terms of Use and Privacy Policy, in the Android wording. The same text is published for Google Play
 * in `store/legal/`; change both together. This version has no AI, camera or location features, so those sections are absent.
 */
@Composable
fun LegalTextScreen(document: LegalDocument, onBack: () -> Unit) {
    val title = if (document == LegalDocument.Terms) R.string.terms_title else R.string.privacy_title
    val intro = if (document == LegalDocument.Terms) R.string.terms_intro else R.string.privacy_intro
    val sections = if (document == LegalDocument.Terms) termsSections else privacySections
    Column(
        modifier = Modifier.fillMaxSize().background(Palette.background).statusBarsPadding().navigationBarsPadding(),
    ) {
        Text(
            text = "‹  " + stringResource(R.string.legal_gate_read_again),
            style = DsText.callout,
            color = Palette.accent,
            modifier = Modifier.clickable(onClick = onBack).padding(Space.lg),
        )
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Text(text = stringResource(title), style = DsText.titleScreen, color = Palette.textPrimary)
            Text(text = stringResource(R.string.privacy_updated), style = DsText.meta, color = Palette.textTertiary)
            Text(text = stringResource(intro), style = DsText.body, color = Palette.textPrimary)
            sections.forEach { section ->
                Column(
                    modifier = Modifier.fillMaxWidth().dsCard(Radius.md).padding(Space.md),
                    verticalArrangement = Arrangement.spacedBy(Space.xxs),
                ) {
                    Text(text = stringResource(section.title), style = DsText.headline, color = Palette.textPrimary)
                    Text(text = stringResource(section.body), style = DsText.body, color = Palette.textSecondary)
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = Space.xl))
        }
    }
}
