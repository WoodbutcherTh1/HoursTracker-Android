package com.hourstracker.app.ui.nav

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Lets a screen with an editable draft (Settings) stop the tab bar from leaving it with unsaved changes.
 * The screen registers while it is on screen; [AppRoot] asks before it navigates away.
 */
class UnsavedGuard {
    private var hasUnsaved: () -> Boolean = { false }
    private var saveAction: () -> Unit = {}
    private var discardAction: () -> Unit = {}

    fun register(hasUnsaved: () -> Boolean, save: () -> Unit, discard: () -> Unit) {
        this.hasUnsaved = hasUnsaved
        saveAction = save
        discardAction = discard
    }

    fun unregister() = register({ false }, {}, {})

    fun hasUnsavedChanges(): Boolean = hasUnsaved()

    fun save() = saveAction()

    fun discard() = discardAction()
}

val LocalUnsavedGuard = staticCompositionLocalOf { UnsavedGuard() }
