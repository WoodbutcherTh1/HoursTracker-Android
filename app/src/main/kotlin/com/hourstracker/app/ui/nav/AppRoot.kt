package com.hourstracker.app.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import com.hourstracker.app.data.AppLanguage
import com.hourstracker.app.ui.export.ExportScreen
import com.hourstracker.app.LocalAppContainer
import com.hourstracker.app.ui.history.HistoryScreen
import com.hourstracker.app.ui.manual.ManualEntryScreen
import com.hourstracker.app.ui.home.HomeScreen
import com.hourstracker.app.ui.payslips.PayslipsScreen
import com.hourstracker.app.ui.settings.SettingsScreen
import com.hourstracker.app.ui.theme.Palette
import com.hourstracker.app.ui.theme.Space

private const val MANUAL_ENTRY_ROUTE = "history/new"
private const val EDIT_ROUTE = "history/edit"

/** The signed-in shell: five tabs with a back stack each, and the floating tab bar over the content. */
@Composable
fun AppRoot(language: AppLanguage, onLanguageChange: (AppLanguage) -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = TopLevelTab.entries.firstOrNull { tab ->
        backStackEntry?.destination?.hierarchy?.any { it.route == tab.route } == true
    } ?: TopLevelTab.Home

    Box(modifier = Modifier.fillMaxSize().background(Palette.background)) {
        NavHost(
            navController = navController,
            startDestination = TopLevelTab.Home.route,
            // Tabs cross-fade; pushed screens (manual entry, edit) slide in from the end edge.
            enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 12 } },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(240)) { it / 12 } },
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
        ) {
            composable(TopLevelTab.Home.route) { HomeScreen() }
            composable(TopLevelTab.History.route) {
                HistoryScreen(onAdd = { navController.navigate(MANUAL_ENTRY_ROUTE) }, onEdit = { navController.navigate("$EDIT_ROUTE/$it") })
            }
            composable(MANUAL_ENTRY_ROUTE) { ManualEntryScreen(LocalAppContainer.current, editId = null, onClose = { navController.popBackStack() }) }
            composable("$EDIT_ROUTE/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                ManualEntryScreen(LocalAppContainer.current, editId = id, onClose = { navController.popBackStack() })
            }
            composable(TopLevelTab.Payslips.route) { PayslipsScreen() }
            composable(TopLevelTab.Export.route) { ExportScreen() }
            composable(TopLevelTab.Settings.route) { SettingsScreen(language, onLanguageChange) }
        }
        if (backStackEntry?.destination?.route.let { it != MANUAL_ENTRY_ROUTE && it?.startsWith(EDIT_ROUTE) != true }) FloatingTabBar(
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
