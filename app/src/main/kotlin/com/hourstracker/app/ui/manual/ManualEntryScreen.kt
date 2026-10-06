package com.hourstracker.app.ui.manual

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.hourstracker.app.R
import com.hourstracker.app.data.AppContainer
import com.hourstracker.app.domain.ManualEntry
import com.hourstracker.app.domain.ManualEntryInput
import com.hourstracker.app.ui.components.FormCard
import com.hourstracker.app.ui.components.FormRow
import com.hourstracker.app.ui.components.PickerRow
import com.hourstracker.app.ui.components.PrimaryButton
import com.hourstracker.app.ui.components.RowDivider
import com.hourstracker.app.ui.components.SectionHeader
import com.hourstracker.app.ui.components.StepperRow
import com.hourstracker.app.ui.components.TextRow
import com.hourstracker.app.ui.components.ToggleRow
import com.hourstracker.app.ui.theme.DsText
import com.hourstracker.app.ui.theme.Palette
import com.hourstracker.app.ui.theme.Space
import com.hourstracker.model.DayType
import java.time.Instant
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Add a shift by hand: a day, its type, clock times or a total, a break, and notes. */
@Composable
fun ManualEntryScreen(container: AppContainer, onClose: () -> Unit) {
    val calendar = remember { container.deviceCalendar() }
    val settings by container.settings.settings.collectAsState()
    val shifts by container.shifts.shifts.collectAsState(initial = emptyList())
    val existing = remember(shifts) { shifts.map { it.session } }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val savedToast = stringResource(R.string.feedback_session_saved)

    var dateEpochDay by rememberSaveable { mutableStateOf(calendar.localDate(calendar.now()).toEpochDay()) }
    val date = LocalDate.ofEpochDay(dateEpochDay)
    var dayTypeName by rememberSaveable { mutableStateOf(ManualEntry.automaticDayType(date, settings, existing, calendar).name) }
    val dayType = DayType.valueOf(dayTypeName)
    var useDirect by rememberSaveable { mutableStateOf(false) }
    var directTenths by rememberSaveable { mutableIntStateOf(86) }
    var clockIn by rememberSaveable { mutableIntStateOf(8 * 60) }
    var clockOut by rememberSaveable { mutableIntStateOf(8 * 60 + 516) }
    var breakMinutes by rememberSaveable { mutableIntStateOf(if (settings.breaksArePaid) 0 else settings.defaultBreakMinutes) }
    var night by rememberSaveable { mutableStateOf(false) }
    var notes by rememberSaveable { mutableStateOf("") }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf<String?>(null) }
    var showSickCap by remember { mutableStateOf(false) }

    val input = ManualEntryInput(date, dayType, useDirect, directTenths / 10.0, clockIn, clockOut, breakMinutes, night, notes)
    val problem = ManualEntry.validate(input)

    fun setDay(newDate: LocalDate) {
        dateEpochDay = newDate.toEpochDay()
        dayTypeName = ManualEntry.automaticDayType(newDate, settings, existing, calendar).name
    }

    fun save() {
        if (dayType == DayType.Sick && ManualEntry.sickCapReached(date, existing, calendar)) {
            showSickCap = true
            return
        }
        val record = ManualEntry.build(input, settings, calendar, calendar.now())
        scope.launch {
            container.shifts.upsert(record)
            Toast.makeText(context, savedToast, Toast.LENGTH_SHORT).show()
            onClose()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Palette.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "✕", style = DsText.titleSection, color = Palette.textSecondary, modifier = Modifier.clickable(onClick = onClose).padding(Space.xs))
            Text(text = stringResource(R.string.manual_title), style = DsText.titleSection, color = Palette.textPrimary)
            Text(
                text = stringResource(R.string.edit_save),
                style = DsText.headline,
                color = if (problem == null) Palette.accent else Palette.textTertiary,
                modifier = Modifier.background(Palette.card, CircleShape).clickable(enabled = problem == null, onClick = ::save)
                    .padding(horizontal = Space.md, vertical = Space.xs),
            )
        }

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.md)) {
            SectionHeader(stringResource(R.string.manual_date))
            FormCard {
                FormRow(label = stringResource(R.string.manual_work_day), modifier = Modifier.clickable { pickDate = true }) {
                    Text(
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date),
                        style = DsText.body,
                        color = Palette.accent,
                    )
                }
            }

            SectionHeader(stringResource(R.string.settings_work_rules))
            FormCard {
                PickerRow(
                    label = stringResource(R.string.session_day_type),
                    selected = dayType,
                    options = DayType.entries,
                    optionLabel = { dayTypeLabel(it) },
                    onSelect = { dayTypeName = it.name },
                )
            }

            when (dayType) {
                DayType.Holiday -> {
                    SectionHeader(stringResource(R.string.manual_holiday_auto_filled_title))
                    FormCard {
                        val expected = settings.expectedShift(date.atStartOfDay(calendar.zone).toInstant(), calendar)
                        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
                        FormRow(stringResource(R.string.edit_clock_in)) { Text(time.format(expected.clockIn.atZone(calendar.zone)), style = DsText.body, color = Palette.textSecondary) }
                        RowDivider()
                        FormRow(stringResource(R.string.edit_clock_out)) { Text(time.format(expected.clockOut.atZone(calendar.zone)), style = DsText.body, color = Palette.textSecondary) }
                        Text(stringResource(R.string.manual_holiday_auto_filled_hint), style = DsText.meta, color = Palette.textSecondary, modifier = Modifier.padding(Space.md))
                    }
                }

                DayType.Sick -> {
                    SectionHeader(stringResource(R.string.day_type_sick))
                    FormCard {
                        val (number, share) = ManualEntry.sickStreakPreview(date, existing, calendar)
                        Text(
                            stringResource(R.string.manual_sick_preview, number, (share * 100).toInt()),
                            style = DsText.sub,
                            color = Palette.textPrimary,
                            modifier = Modifier.padding(Space.md),
                        )
                        Text(stringResource(R.string.manual_sick_hint), style = DsText.meta, color = Palette.textSecondary, modifier = Modifier.padding(horizontal = Space.md).padding(bottom = Space.md))
                    }
                }

                else -> {
                    SectionHeader(stringResource(R.string.manual_entry_mode))
                    FormCard {
                        PickerRow(
                            label = stringResource(R.string.manual_mode),
                            selected = useDirect,
                            options = listOf(false, true),
                            optionLabel = { stringResource(if (it) R.string.manual_total_hours else R.string.manual_clock_in_out) },
                            onSelect = { useDirect = it },
                        )
                    }
                    if (useDirect) {
                        SectionHeader(stringResource(R.string.manual_hours))
                        FormCard {
                            StepperRow(
                                label = stringResource(R.string.manual_hours_format, "%.1f".format(Locale.ROOT, directTenths / 10.0)),
                                value = directTenths,
                                range = 0..240,
                                onValueChange = { directTenths = it },
                                minusDescription = "−",
                                plusDescription = "+",
                            )
                        }
                    } else {
                        SectionHeader(stringResource(R.string.edit_times))
                        FormCard {
                            FormRow(stringResource(R.string.edit_clock_in), modifier = Modifier.clickable { pickTime = "in" }) {
                                Text(clock(clockIn), style = DsText.body.copy(fontFeatureSettings = "tnum"), color = Palette.accent)
                            }
                            RowDivider()
                            FormRow(stringResource(R.string.edit_clock_out), modifier = Modifier.clickable { pickTime = "out" }) {
                                Text(clock(clockOut), style = DsText.body.copy(fontFeatureSettings = "tnum"), color = Palette.accent)
                            }
                        }
                    }
                }
            }

            if (dayType != DayType.Sick) {
                SectionHeader("")
                FormCard {
                    ToggleRow(stringResource(R.string.session_night_shift), night, { night = it })
                    RowDivider()
                    StepperRow(
                        label = stringResource(R.string.session_break_minutes) + "  " + breakMinutes,
                        value = breakMinutes,
                        range = 0..240,
                        step = 5,
                        onValueChange = { breakMinutes = it },
                        minusDescription = "−",
                        plusDescription = "+",
                    )
                }
            }

            if (problem != null) {
                Text(
                    text = stringResource(if (problem == ManualEntry.Problem.ZeroDuration) R.string.manual_error_zero_duration else R.string.manual_error_break_exceeds_shift),
                    style = DsText.sub,
                    color = Palette.overdue,
                    modifier = Modifier.padding(top = Space.md),
                )
            }

            SectionHeader(stringResource(R.string.edit_notes))
            FormCard { TextRow(notes, { notes = it }, stringResource(R.string.edit_notes_placeholder)) }

            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = Space.xl))
        }
        Column(modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm)) {
            PrimaryButton(text = stringResource(R.string.edit_save), onClick = ::save, enabled = problem == null)
        }
    }

    if (pickDate) {
        EntryDateDialog(date, onDismiss = { pickDate = false }, onConfirm = {
            pickDate = false
            setDay(it)
        })
    }
    pickTime?.let { which ->
        val initial = if (which == "in") clockIn else clockOut
        EntryTimeDialog(initial / 60, initial % 60, onDismiss = { pickTime = null }, onConfirm = { h, m ->
            if (which == "in") clockIn = h * 60 + m else clockOut = h * 60 + m
            pickTime = null
        })
    }
    if (showSickCap) {
        AlertDialog(
            onDismissRequest = { showSickCap = false },
            title = { Text(stringResource(R.string.error_title)) },
            text = { Text(stringResource(R.string.sick_day_cap_reached)) },
            confirmButton = { TextButton(onClick = { showSickCap = false }) { Text(stringResource(R.string.error_ok)) } },
        )
    }
}

@Composable
private fun dayTypeLabel(type: DayType): String = stringResource(
    when (type) {
        DayType.Regular -> R.string.day_type_regular
        DayType.RestDay -> R.string.day_type_rest_day
        DayType.Holiday -> R.string.day_type_holiday
        DayType.Sick -> R.string.day_type_sick
    },
)

private fun clock(minutes: Int): String = "%02d:%02d".format(Locale.ROOT, minutes / 60, minutes % 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryDateDialog(initial: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onConfirm(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("✕") } },
    ) { DatePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryTimeDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("✕") } },
        text = { TimePicker(state = state) },
    )
}
