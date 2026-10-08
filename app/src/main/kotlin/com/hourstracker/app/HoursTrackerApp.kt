package com.hourstracker.app

import android.app.Application
import com.hourstracker.app.data.AppContainer
import com.hourstracker.app.data.NotificationChannels

class HoursTrackerApp : Application() {
    lateinit var container: AppContainer
        internal set

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
        container = AppContainer(this)
    }
}
