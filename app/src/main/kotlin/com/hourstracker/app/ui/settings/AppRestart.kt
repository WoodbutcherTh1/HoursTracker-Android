package com.hourstracker.app.ui.settings

import android.content.Context
import android.content.Intent

/** Starts the app from scratch (a new process), as after "Delete all my data": the first screen is the consent screen. */
fun restartApp(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    Runtime.getRuntime().exit(0)
}
