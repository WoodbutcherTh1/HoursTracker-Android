package com.hourstracker.app.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hourstracker.app.ui.export.ExportScreen
import com.hourstracker.app.ui.history.HistoryScreen
import com.hourstracker.app.ui.home.HomeScreen
import com.hourstracker.app.ui.payslips.PayslipsScreen
import com.hourstracker.app.ui.settings.SettingsScreen
import com.hourstracker.app.ui.theme.Palette
import com.hourstracker.app.ui.theme.Space

/** The signed-in shell: five tabs with a back stack each, and the floating tab bar over the content. */
@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = TopLevelTab.entries.firstOrNull { tab ->
        backStackEntry?.destination?.hierarchy?.any { it.route == tab.route } == true
    } ?: TopLevelTab.Home

    Box(modifier = Modifier.fillMaxSize().background(Palette.background)) {
        NavHost(
            navController = navController,
            startDestination = TopLevelTab.Home.route,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
        ) {
            composable(TopLevelTab.Home.route) { HomeScreen() }
            composable(TopLevelTab.History.route) { HistoryScreen() }
            composable(TopLevelTab.Payslips.route) { PayslipsScreen() }
            composable(TopLevelTab.Export.route) { ExportScreen() }
            composable(TopLevelTab.Settings.route) { SettingsScreen() }
        }
        FloatingTabBar(
            selected = currentTab,
            onSelect = { tab ->
                navController.navigate(tab.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = Space.md, vertical = Space.xs),
        )
    }
}
