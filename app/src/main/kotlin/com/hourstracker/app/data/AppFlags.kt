package com.hourstracker.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The app-language choice. [System] follows the device. */
enum class AppLanguage(val tag: String?, val label: String?) {
    System(null, null),
    English("en", "English"),
    Hebrew("iw", "עברית"),
}

/** First-run state: legal consent, onboarding, and the language chosen inside the app. */
class AppFlags(context: Context) {
    private val prefs = context.getSharedPreferences("flags", Context.MODE_PRIVATE)

    private val consentFlow = MutableStateFlow(prefs.getInt(KEY_CONSENT, 0))
    private val onboardingFlow = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDING, false))

    /** The accepted version of the terms and privacy policy; 0 when nothing was accepted yet. */
    val acceptedLegalVersion: StateFlow<Int> = consentFlow.asStateFlow()
    val onboardingDone: StateFlow<Boolean> = onboardingFlow.asStateFlow()

    fun acceptLegal() {
        prefs.edit { putInt(KEY_CONSENT, CURRENT_LEGAL_VERSION) }
        consentFlow.value = CURRENT_LEGAL_VERSION
    }

    fun finishOnboarding() {
        prefs.edit { putBoolean(KEY_ONBOARDING, true) }
        onboardingFlow.value = true
    }

    var language: AppLanguage
        get() = AppLanguage.entries.firstOrNull { it.name == prefs.getString(KEY_LANGUAGE, null) } ?: AppLanguage.System
        set(value) = prefs.edit { putString(KEY_LANGUAGE, value.name) }

    companion object {
        const val CURRENT_LEGAL_VERSION = 1
        private const val KEY_CONSENT = "legalVersion"
        private const val KEY_ONBOARDING = "onboardingDone"
        private const val KEY_LANGUAGE = "language"
    }
}
