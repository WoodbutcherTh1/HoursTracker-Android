package com.hourstracker.app

import android.app.Application
import com.hourstracker.app.data.AppContainer

class HoursTrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
