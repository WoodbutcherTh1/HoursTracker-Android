package com.hourstracker.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hourstracker.app.data.AppFlags
import com.hourstracker.app.data.AppLanguage
import com.hourstracker.app.ui.nav.AppRoot
import com.hourstracker.app.ui.theme.HoursTrackerTheme
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
            HoursTrackerTheme {
                AppRoot(
                    language = container.flags.language,
                    onLanguageChange = { chosen: AppLanguage ->
                        container.flags.language = chosen
                        recreate()
                    },
                )
            }
        }
    }
}
