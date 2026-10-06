package com.hourstracker.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.hourstracker.app.data.AppContainer

/** A factory that hands the manual [AppContainer] to a ViewModel's constructor (no Hilt). */
@Composable
inline fun <reified VM : ViewModel> viewModelFactory(crossinline create: (AppContainer) -> VM): ViewModelProvider.Factory {
    val container = (LocalContext.current.applicationContext as HoursTrackerApp).container
    return object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = create(container) as T
    }
}

/** The app's manual dependency container, available to every screen. */
val LocalAppContainer = androidx.compose.runtime.staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
