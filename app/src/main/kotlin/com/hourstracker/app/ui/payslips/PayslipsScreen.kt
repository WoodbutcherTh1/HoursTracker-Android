package com.hourstracker.app.ui.payslips

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hourstracker.app.R
import com.hourstracker.app.ui.theme.DsText
import com.hourstracker.app.ui.theme.Palette

@Composable
fun PayslipsScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = stringResource(R.string.tab_payslips), style = DsText.titleScreen, color = Palette.textPrimary)
    }
}
