package com.hourstracker.app

import android.app.Application
import com.hourstracker.app.data.AppContainer
import com.hourstracker.app.data.NotificationChannels

open class HoursTrackerApp : Application() {
    lateinit var container: AppContainer
        internal set

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
        container = createContainer()
    }

    /** The one place the container is built; tests override it to start from a known state. */
    protected open fun createContainer(): AppContainer = AppContainer(this)
}
