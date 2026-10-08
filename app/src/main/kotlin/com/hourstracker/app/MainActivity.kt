package com.hourstracker.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.hourstracker.app.data.AppFlags
import com.hourstracker.app.data.AppLanguage
import com.hourstracker.app.ui.nav.AppGate
import com.hourstracker.app.ui.theme.HoursTrackerTheme
import com.hourstracker.app.ui.theme.ThemeMode
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val container get() = (application as HoursTrackerApp).container

    /** Applies the language chosen inside the app (System keeps the device language), including RTL. */
    override fun attachBaseContext(newBase: Context) {
        val tag = AppFlags(newBase).language.tag
        if (tag == null) {
            super.attachBaseContext(newBase)
            return
        }
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val configuration = Configuration(newBase.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by container.settings.themeMode.collectAsState()
            // The status and navigation bar icons must contrast with the app's own theme, which can differ from the phone's.
            val dark = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.AUTO -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            androidx.compose.runtime.LaunchedEffect(dark) {
                val bars = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            HoursTrackerTheme(themeMode = themeMode) {
                androidx.compose.runtime.CompositionLocalProvider(LocalAppContainer provides container) {
                    AppGate(
                    container = container,
                    onLanguageChange = { chosen: AppLanguage ->
                        container.flags.language = chosen
                        recreate()
                    },
                    )
                }
            }
        }
    }
}
