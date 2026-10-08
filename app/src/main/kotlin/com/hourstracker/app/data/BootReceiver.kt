package com.hourstracker.app.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hourstracker.app.HoursTrackerApp

/** Alarms are cleared when the phone restarts. Starting the app process rebuilds the container, which re-arms the reminder. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        (context.applicationContext as HoursTrackerApp).container
    }
}
