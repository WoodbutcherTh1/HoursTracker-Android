package com.hourstracker.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.hourstracker.app.data.AppContainer
import com.hourstracker.app.data.AppFlags
import com.hourstracker.app.data.AppLanguage
import com.hourstracker.app.ui.legal.LegalConsentScreen
import com.hourstracker.app.ui.onboarding.OnboardingScreen

/** What the app shows first: legal consent, then onboarding, then the five tabs. Nothing is usable behind consent. */
@Composable
fun AppGate(container: AppContainer, onLanguageChange: (AppLanguage) -> Unit, currentLegalVersion: Int = AppFlags.CURRENT_LEGAL_VERSION) {
    val acceptedVersion by container.flags.acceptedLegalVersion.collectAsState()
    val onboardingDone by container.flags.onboardingDone.collectAsState()
    val language = container.flags.language

    when {
        AppFlags.needsConsent(acceptedVersion, currentLegalVersion) -> LegalConsentScreen(
            isReconsent = acceptedVersion > 0,
            language = language,
            onLanguageChange = onLanguageChange,
            onAccept = { container.acceptLegal(currentLegalVersion) },
        )

        !onboardingDone -> {
            val settings by container.settings.settings.collectAsState()
            OnboardingScreen(
                settings = settings,
                calendar = container.deviceCalendar(),
                language = language,
                onLanguageChange = onLanguageChange,
                onFinish = { answers ->
                    // Only what was answered is saved: the rate goes into the pay settings, the rest is display only.
                    answers.rate?.let { rate ->
                        container.settings.save(settings.copy(hourlyRate = rate), container.settings.profile.value, null)
                    }
                    answers.pattern?.let {
                        container.flags.weekPattern = it.name
                        container.flags.customWorkdays = answers.customDays
                    }
                    answers.weeklyHours?.let { container.flags.weeklyGoalHours = it }
                    container.flags.finishOnboarding()
                },
            )
        }

        else -> AppRoot(language = language, onLanguageChange = onLanguageChange)
    }
}
