package com.hourstracker.app.ui.nav

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.vector.ImageVector
import com.hourstracker.app.R

/** The five top-level tabs, in the same order as iOS. Each keeps its own back stack. */
enum class TopLevelTab(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    Home("home", R.string.tab_home, Icons.Filled.Home),
    History("history", R.string.tab_history, Icons.Filled.Menu),
    Payslips("payslips", R.string.tab_payslips, Icons.Filled.Create),
    Export("export", R.string.tab_export, Icons.Filled.Share),
    Settings("settings", R.string.tab_settings, Icons.Filled.Settings),
}
