package com.hourstracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hourstracker.app.ui.nav.AppRoot
import com.hourstracker.app.ui.theme.HoursTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HoursTrackerTheme {
                AppRoot()
            }
        }
    }
}
