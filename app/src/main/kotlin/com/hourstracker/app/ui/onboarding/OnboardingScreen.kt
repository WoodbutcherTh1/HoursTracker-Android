package com.hourstracker.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hourstracker.app.R
import com.hourstracker.app.data.AppLanguage
import com.hourstracker.app.ui.components.PayCard
import com.hourstracker.app.ui.components.PayCardRow
import com.hourstracker.app.ui.components.PayTierSegment
import com.hourstracker.app.ui.components.PickerRow
import com.hourstracker.app.ui.components.PrimaryButton
import com.hourstracker.app.ui.theme.DsText
import com.hourstracker.app.ui.theme.Palette
import com.hourstracker.app.ui.theme.Radius
import com.hourstracker.app.ui.theme.Space
import com.hourstracker.app.ui.theme.dsCard
import com.hourstracker.model.HistoryPeriodHelper
import com.hourstracker.model.IosCalendar
import com.hourstracker.model.PayFormatter
import com.hourstracker.model.WorkplaceSettings
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

private enum class Step(val progress: Int) {
    Welcome(0),
    Rate(1),
    WorkType(2),
    CustomDays(2),
    WeeklyHours(3),
    Result(4),
}

/** What the worker answered; the screen hands it back when onboarding ends. */
class OnboardingAnswers(
    val rate: Double?,
    val pattern: WeekPattern?,
    val customDays: Set<Int>,
    val weeklyHours: Int?,
    val clockInNow: Boolean,
)

/**
 * First-run questions: welcome, hourly rate, typical week, weekly hours, and a result card priced by the real pay engine.
 * Answers survive a language change (the activity restarts) because they are saved with the screen state.
 */
@Composable
fun OnboardingScreen(
    settings: WorkplaceSettings,
    calendar: IosCalendar,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onFinish: (OnboardingAnswers) -> Unit,
) {
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    var rateText by rememberSaveable { mutableStateOf(if (settings.hourlyRate > 0) settings.hourlyRate.toString().removeSuffix(".0") else "") }
    var patternIndex by rememberSaveable { mutableIntStateOf(0) }
    var customDays by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var weeklyHours by rememberSaveable { mutableIntStateOf(42) }
    var touchedHours by rememberSaveable { mutableStateOf(false) }
    var answeredWeek by rememberSaveable { mutableStateOf(false) }
    var answeredHours by rememberSaveable { mutableStateOf(false) }

    val step = Step.entries[stepIndex]
    val pattern = WeekPattern.entries[patternIndex]
    val rate = OnboardingEstimate.parseRate(rateText)
    val preview = remember(rate, settings) { settings.copy(hourlyRate = rate ?: settings.hourlyRate) }
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

    fun goTo(next: Step) {
        stepIndex = next.ordinal
    }

    fun finish(clockIn: Boolean, skipped: Boolean = false) {
        onFinish(
            OnboardingAnswers(
                rate = rate,
                pattern = if (answeredWeek && !skipped) pattern else null,
                customDays = if (pattern == WeekPattern.Custom) customDays.toSet() else emptySet(),
                weeklyHours = if (answeredHours && !skipped) weeklyHours else null,
                clockInNow = clockIn,
            ),
        )
    }

    fun advance() {
        when (step) {
            Step.Welcome -> goTo(Step.Rate)
            Step.Rate -> goTo(Step.WorkType)
            Step.WorkType -> {
                answeredWeek = true
                goTo(if (pattern == WeekPattern.Custom) Step.CustomDays else Step.WeeklyHours)
            }
            Step.CustomDays -> goTo(Step.WeeklyHours)
            Step.WeeklyHours -> {
                answeredHours = true
                goTo(Step.Result)
            }
            Step.Result -> finish(clockIn = false)
        }
    }

    fun back() {
        when (step) {
            Step.Welcome -> Unit
            Step.Rate -> goTo(Step.Welcome)
            Step.WorkType -> goTo(Step.Rate)
            Step.CustomDays -> goTo(Step.WorkType)
            Step.WeeklyHours -> goTo(if (pattern == WeekPattern.Custom) Step.CustomDays else Step.WorkType)
            Step.Result -> goTo(Step.WeeklyHours)
        }
    }

    val canAdvance = when (step) {
        Step.Rate -> rate != null
        Step.CustomDays -> customDays.isNotEmpty()
        else -> true
    }

    Column(modifier = Modifier.fillMaxSize().background(Palette.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        TopBar(step = step, language = language, onLanguageChange = onLanguageChange, onBack = ::back, onSkip = { finish(clockIn = false, skipped = true) })

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.lg, vertical = Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            when (step) {
                Step.Welcome -> WelcomeStep(settings.currencyCode)
                Step.Rate -> RateStep(rateText, { rateText = it }, rate != null, currencySymbol(settings.currencyCode, locale))
                Step.WorkType -> WorkTypeStep(pattern) {
                    patternIndex = it.ordinal
                    touchedHours = false
                }
                Step.CustomDays -> CustomDaysStep(customDays.toSet(), locale) { day ->
                    customDays = if (day in customDays) customDays - day else customDays + day
                    touchedHours = false
                }
                Step.WeeklyHours -> {
                    // Start from what the chosen week suggests until the worker moves the slider.
                    val suggested = pattern.defaultWeeklyHours(customDays.toSet())
                    if (!touchedHours && weeklyHours != suggested) weeklyHours = suggested
                    WeeklyHoursStep(
                        hours = weeklyHours,
                        onHours = {
                            weeklyHours = it
                            touchedHours = true
                        },
                        estimate = OnboardingEstimate.weeklyGross(preview, calendar, weeklyHours, pattern.workdays(customDays.toSet()).size),
                        currencyCode = preview.currencyCode,
                        locale = locale,
                    )
                }
                Step.Result -> ResultStep(preview, calendar, locale)
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = Space.lg).padding(bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            if (step == Step.Result) {
                Text(
                    text = stringResource(R.string.onb_clock_in_now),
                    style = DsText.headline,
                    color = Palette.accent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().height(44.dp).clickable { finish(clockIn = true) }.padding(top = 10.dp),
                )
            }
            PrimaryButton(
                text = stringResource(
                    when (step) {
                        Step.Welcome -> R.string.onb_cta_start
                        Step.Rate, Step.WorkType, Step.CustomDays -> R.string.onboarding_next
                        Step.WeeklyHours -> R.string.onb_cta_show_result
                        Step.Result -> R.string.onboarding_start
                    },
                ),
                onClick = ::advance,
                enabled = canAdvance,
            )
        }
    }
}

@Composable
private fun TopBar(step: Step, language: AppLanguage, onLanguageChange: (AppLanguage) -> Unit, onBack: () -> Unit, onSkip: () -> Unit) {
    val languageDescription = stringResource(R.string.onb_language)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                if (step != Step.Welcome) {
                    Text(
                        text = "‹",
                        style = DsText.titleScreen,
                        color = Palette.accent,
                        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.onb_back), onClick = onBack),
                    )
                }
                // The language picker keeps each language in its own script so anyone can find their way back.
                Box(Modifier.width(150.dp).semantics { contentDescription = languageDescription }) {
                    PickerRow(
                        label = "",
                        selected = language,
                        options = AppLanguage.entries,
                        optionLabel = { it.label ?: stringResource(R.string.settings_language_system) },
                        onSelect = onLanguageChange,
                        modifier = Modifier.height(40.dp),
                    )
                }
            }
            if (step != Step.Result) {
                Text(
                    text = stringResource(R.string.onb_skip),
                    style = DsText.callout,
                    color = Palette.textSecondary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.clickable(onClick = onSkip).padding(start = Space.sm),
                )
            }
        }
        if (step != Step.Welcome) {
            val progressDescription = stringResource(R.string.onb_progress, step.progress.coerceAtLeast(1).toString(), "4")
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.lg).semantics { contentDescription = progressDescription },
                horizontalArrangement = Arrangement.spacedBy(Space.xxs),
            ) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier.weight(1f).height(4.dp)
                            .background(if (index < step.progress) Palette.accent else Palette.raised, CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun StepTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(text = title, style = DsText.titleScreen, color = Palette.textPrimary)
        if (subtitle != null) Text(text = subtitle, style = DsText.sub, color = Palette.textSecondary)
    }
}

@Composable
private fun WelcomeStep(currencyCode: String) {
    PayCard(
        amount = 412.50,
        currencyCode = currencyCode,
        title = stringResource(R.string.onb_today),
        caption = stringResource(R.string.onb_example_caption),
        segments = listOf(PayTierSegment(7.0, Palette.accent), PayTierSegment(2.0, Palette.ot125), PayTierSegment(1.0, Palette.ot150)),
        rows = emptyList(),
    )
    Text(text = stringResource(R.string.onb_welcome_title), style = DsText.titleScreen, color = Palette.textPrimary)
    listOf(R.string.onb_welcome_row1, R.string.onb_welcome_row2).forEach { row ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Box(Modifier.size(8.dp).background(Palette.accent, CircleShape))
            Text(text = stringResource(row), style = DsText.body, color = Palette.textSecondary)
        }
    }
}

@Composable
private fun RateStep(text: String, onText: (String) -> Unit, valid: Boolean, symbol: String) {
    val rateDescription = stringResource(R.string.onb_rate_a11y)
    StepTitle(stringResource(R.string.onb_rate_title), stringResource(R.string.onb_rate_subtitle))
    OutlinedTextField(
        value = text,
        onValueChange = onText,
        singleLine = true,
        prefix = { Text(symbol, style = DsText.numLarge, color = Palette.textSecondary) },
        textStyle = DsText.numLarge,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(Radius.lg),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Palette.textPrimary,
            unfocusedTextColor = Palette.textPrimary,
            focusedBorderColor = Palette.accent,
            unfocusedBorderColor = Palette.hairline,
            cursorColor = Palette.accent,
            focusedContainerColor = Palette.card,
            unfocusedContainerColor = Palette.card,
        ),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = rateDescription },
    )
    if (text.isNotEmpty() && !valid) {
        Text(text = stringResource(R.string.onb_rate_error), style = DsText.sub, color = Palette.warning)
    }
}

@Composable
private fun WorkTypeStep(selected: WeekPattern, onSelect: (WeekPattern) -> Unit) {
    StepTitle(stringResource(R.string.onb_week_title))
    listOf(
        Triple(WeekPattern.FiveDays, R.string.onb_week_five, R.string.onb_week_five_sub),
        Triple(WeekPattern.SixDays, R.string.onb_week_six, R.string.onb_week_six_sub),
        Triple(WeekPattern.Varies, R.string.onb_week_varies, R.string.onb_week_varies_sub),
        Triple(WeekPattern.Custom, R.string.onb_week_custom, R.string.onb_week_custom_sub),
    ).forEach { (pattern, title, subtitle) ->
        val chosen = pattern == selected
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .dsCard(Radius.lg, if (chosen) Palette.raised else Palette.card)
                .clickable { onSelect(pattern) }
                .padding(Space.md),
        ) {
            Text(text = stringResource(title), style = DsText.headline, color = if (chosen) Palette.accent else Palette.textPrimary)
            Text(text = stringResource(subtitle), style = DsText.sub, color = Palette.textSecondary)
        }
    }
}

@Composable
private fun CustomDaysStep(selected: Set<Int>, locale: Locale, onToggle: (Int) -> Unit) {
    StepTitle(stringResource(R.string.onb_days_title), stringResource(R.string.onb_days_subtitle))
    (1..7).chunked(4).forEach { rowDays ->
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            rowDays.forEach { weekday ->
                val chosen = weekday in selected
                Text(
                    text = shortWeekdayName(weekday, locale),
                    style = DsText.sub,
                    color = if (chosen) Palette.ink else Palette.textPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .background(if (chosen) Palette.accent else Palette.card, RoundedCornerShape(Radius.md))
                        .clickable { onToggle(weekday) }
                        .padding(top = 13.dp),
                )
            }
            repeat(4 - rowDays.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun WeeklyHoursStep(hours: Int, onHours: (Int) -> Unit, estimate: Double, currencyCode: String, locale: Locale) {
    StepTitle(stringResource(R.string.onb_hours_title))
    Text(
        text = stringResource(R.string.onb_hours_value, hours.toString()),
        style = DsText.numHero,
        color = Palette.textPrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    val range = OnboardingEstimate.WEEKLY_HOURS_RANGE
    Slider(
        value = hours.toFloat(),
        onValueChange = { onHours(it.toInt()) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = range.last - range.first - 1,
        colors = SliderDefaults.colors(thumbColor = Palette.accent, activeTrackColor = Palette.accent, inactiveTrackColor = Palette.raised),
    )
    Text(text = stringResource(R.string.onb_hours_note), style = DsText.meta, color = Palette.textTertiary)
    Column(modifier = Modifier.fillMaxWidth().dsCard().padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
        Text(
            text = stringResource(R.string.onb_hours_estimate, PayFormatter.string(estimate, currencyCode, locale)),
            style = DsText.numLarge,
            color = Palette.accent,
            maxLines = 1,
        )
        Text(text = stringResource(R.string.onb_estimate_caption), style = DsText.meta, color = Palette.textTertiary)
    }
}

@Composable
private fun ResultStep(settings: WorkplaceSettings, calendar: IosCalendar, locale: Locale) {
    val day = OnboardingEstimate.day(settings, calendar)
    Text(text = stringResource(R.string.onb_result_title), style = DsText.titleSection, color = Palette.textSecondary)
    PayCard(
        amount = day.grossPay,
        currencyCode = day.currencyCode,
        caption = stringResource(R.string.onb_result_caption),
        segments = listOf(
            PayTierSegment(day.regularHours, Palette.accent),
            PayTierSegment(day.ot125Hours, Palette.ot125),
            PayTierSegment(day.ot150Hours, Palette.ot150),
        ),
        rows = listOf(
            PayCardRow(stringResource(R.string.onb_result_hours), HistoryPeriodHelper.formatHoursClock(day.totalHours)),
            PayCardRow(stringResource(R.string.onb_result_per_hour), PayFormatter.string(settings.hourlyRate, day.currencyCode, locale)),
        ),
    )
    Text(text = stringResource(R.string.onb_result_privacy), style = DsText.sub, color = Palette.textSecondary)
}

private fun shortWeekdayName(foundationWeekday: Int, locale: Locale): String =
    DayOfWeek.of(if (foundationWeekday == 1) 7 else foundationWeekday - 1).getDisplayName(TextStyle.SHORT, locale)

private fun currencySymbol(code: String, locale: Locale): String = java.util.Currency.getInstance(code).getSymbol(locale)
