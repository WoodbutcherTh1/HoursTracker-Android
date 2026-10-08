package com.hourstracker.app.ui.nav

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.hourstracker.app.R

/** The five top-level tabs, in the same order as iOS. Each keeps its own back stack. */
enum class TopLevelTab(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    Home("home", R.string.tab_home, TabIcons.Home),
    History("history", R.string.tab_history, TabIcons.History),
    Payslips("payslips", R.string.tab_payslips, TabIcons.Payslips),
    Export("export", R.string.tab_export, TabIcons.Export),
    Settings("settings", R.string.tab_settings, TabIcons.Settings),
}
