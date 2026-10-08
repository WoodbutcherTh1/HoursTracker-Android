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
    Arabic("ar", "العربية"),
    Russian("ru", "Русский"),
    ;

    /** Whether text runs right to left in this language; null for [System], which depends on the device. */
    val isRtl: Boolean? get() = tag?.let(::isRtlLanguage)

    companion object {
        private val RTL_LANGUAGES = setOf("he", "iw", "ar", "fa", "ur")

        /** True for right-to-left languages. Accepts a bare language code or a full tag such as "he-IL". */
        fun isRtlLanguage(tag: String): Boolean = tag.substringBefore('-').substringBefore('_').lowercase() in RTL_LANGUAGES
    }
}

private fun isRtlLanguage(tag: String): Boolean = AppLanguage.isRtlLanguage(tag)

/** First-run state: legal consent, onboarding, and the language chosen inside the app. */
class AppFlags(context: Context) {
    private val prefs = context.getSharedPreferences("flags", Context.MODE_PRIVATE)

    private val consentFlow = MutableStateFlow(prefs.getInt(KEY_CONSENT, 0))
    private val onboardingFlow = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDING, false))
    private val showNetFlow = MutableStateFlow(prefs.getBoolean(KEY_SHOW_NET, true))

    /** The accepted version of the terms and privacy policy; 0 when nothing was accepted yet. */
    val acceptedLegalVersion: StateFlow<Int> = consentFlow.asStateFlow()
    val onboardingDone: StateFlow<Boolean> = onboardingFlow.asStateFlow()

    /** Gross or net: the choice shared by the live pay on Home and the day summary. */
    val showNet: StateFlow<Boolean> = showNetFlow.asStateFlow()

    fun setShowNet(value: Boolean) {
        prefs.edit { putBoolean(KEY_SHOW_NET, value) }
        showNetFlow.value = value
    }

    fun acceptLegal() {
        prefs.edit { putInt(KEY_CONSENT, CURRENT_LEGAL_VERSION) }
        consentFlow.value = CURRENT_LEGAL_VERSION
    }

    fun finishOnboarding() {
        prefs.edit { putBoolean(KEY_ONBOARDING, true) }
        onboardingFlow.value = true
    }

    // Onboarding answers that are not workplace settings. DISPLAY ONLY: they never reach the pay engine.
    var weekPattern: String?
        get() = prefs.getString(KEY_WEEK_PATTERN, null)
        set(value) = prefs.edit { putString(KEY_WEEK_PATTERN, value) }

    var customWorkdays: Set<Int>
        get() = prefs.getStringSet(KEY_CUSTOM_DAYS, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
        set(value) = prefs.edit { putStringSet(KEY_CUSTOM_DAYS, value.map { it.toString() }.toSet()) }

    var weeklyGoalHours: Int?
        get() = if (prefs.contains(KEY_WEEKLY_GOAL)) prefs.getInt(KEY_WEEKLY_GOAL, 0) else null
        set(value) = prefs.edit { if (value == null) remove(KEY_WEEKLY_GOAL) else putInt(KEY_WEEKLY_GOAL, value) }

    /** Debug builds fill History with mock shifts once; this remembers that it happened. */
    var mockSeeded: Boolean
        get() = prefs.getBoolean(KEY_MOCK_SEEDED, false)
        set(value) = prefs.edit { putBoolean(KEY_MOCK_SEEDED, value) }

    var language: AppLanguage
        get() = AppLanguage.entries.firstOrNull { it.name == prefs.getString(KEY_LANGUAGE, null) } ?: AppLanguage.System
        set(value) = prefs.edit { putString(KEY_LANGUAGE, value.name) }

    companion object {
        const val CURRENT_LEGAL_VERSION = 1
        private const val KEY_CONSENT = "legalVersion"
        private const val KEY_ONBOARDING = "onboardingDone"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_SHOW_NET = "showNet"
        private const val KEY_MOCK_SEEDED = "mockSeeded"
        private const val KEY_WEEK_PATTERN = "weekPattern"
        private const val KEY_CUSTOM_DAYS = "customWorkdays"
        private const val KEY_WEEKLY_GOAL = "weeklyGoalHoursDisplayOnly"
    }
}
