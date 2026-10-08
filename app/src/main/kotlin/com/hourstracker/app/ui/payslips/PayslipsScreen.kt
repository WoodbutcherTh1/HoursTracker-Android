package com.hourstracker.app.ui.payslips

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hourstracker.app.R
import com.hourstracker.app.ui.components.EmptyState
import com.hourstracker.app.ui.nav.TabIcons
import com.hourstracker.app.ui.theme.DsText
import com.hourstracker.app.ui.theme.Palette
import com.hourstracker.app.ui.theme.Space

/** Payslips are not stored yet, so for now this is the empty state only. */
@Composable
fun PayslipsScreen() {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.tab_payslips),
            style = DsText.titleScreen,
            color = Palette.textPrimary,
            modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm),
        )
        Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = TabIcons.Payslips,
                title = stringResource(R.string.payslips_empty_title),
                body = stringResource(R.string.payslips_empty_body),
            )
        }
    }
}
