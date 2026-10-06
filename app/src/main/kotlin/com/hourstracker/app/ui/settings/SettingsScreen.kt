package com.hourstracker.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hourstracker.app.R
import com.hourstracker.app.data.AppLanguage
import com.hourstracker.app.ui.components.FormCard
import com.hourstracker.app.ui.components.FormRow
import com.hourstracker.app.ui.components.NumberRow
import com.hourstracker.app.ui.components.PickerRow
import com.hourstracker.app.ui.components.RowDivider
import com.hourstracker.app.ui.components.SectionHeader
import com.hourstracker.app.ui.components.StepperRow
import com.hourstracker.app.ui.components.TextRow
import com.hourstracker.app.ui.components.ToggleRow
import com.hourstracker.app.ui.theme.DsText
import com.hourstracker.app.ui.theme.Palette
import com.hourstracker.app.ui.theme.Space
import com.hourstracker.app.util.IsraeliIdValidator
import com.hourstracker.app.viewModelFactory
import com.hourstracker.model.HistoryPeriodHelper
import com.hourstracker.model.MaritalStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Currency
import java.util.Locale

private val CURRENCIES = listOf("ILS", "USD", "EUR", "GBP")

@Composable
fun SettingsScreen(language: AppLanguage, onLanguageChange: (AppLanguage) -> Unit) {
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory { container ->
        SettingsViewModel(container.settings, container.deviceCalendar())
    })
    val d = vm.draft
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val currencySymbol = Currency.getInstance(d.currencyCode).getSymbol(locale)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = stringResource(R.string.settings_title), style = DsText.titleScreen, color = Palette.textPrimary)
            val canSave = vm.hasUnsavedChanges
            val unsavedDescription = stringResource(R.string.settings_unsaved_title)
            Text(
                text = stringResource(if (vm.justSaved) R.string.settings_saved else R.string.settings_save),
                style = DsText.headline,
                color = if (canSave) Palette.accent else Palette.textTertiary,
                modifier = Modifier
                    .background(Palette.card, CircleShape)
                    .clickable(enabled = canSave, onClick = vm::save)
                    .semantics { if (canSave) stateDescription = unsavedDescription }
                    .padding(horizontal = Space.md, vertical = Space.xs),
            )
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.md),
        ) {
            // Worker information
            SectionHeader(stringResource(R.string.settings_worker_info), icon = Icons.Filled.Person)
            FormCard {
                TextRow(d.fullName, { v -> vm.edit { it.copy(fullName = v) } }, stringResource(R.string.settings_full_name))
                RowDivider()
                if (vm.isEditingIdNumber) {
                    TextRow(
                        d.idNumber,
                        { v -> vm.edit { it.copy(idNumber = v.filter(Char::isDigit).take(9)) } },
                        stringResource(R.string.settings_id_number),
                        keyboardType = KeyboardType.NumberPassword,
                    )
                    if (IsraeliIdValidator.shouldWarn(d.idNumber)) {
                        Row(
                            modifier = Modifier.padding(horizontal = Space.md, vertical = Space.xxs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.xs),
                        ) {
                            androidx.compose.material3.Icon(Icons.Filled.Warning, null, tint = Palette.warning, modifier = Modifier.size(16.dp))
                            Text(stringResource(R.string.settings_id_checksum_warning), style = DsText.meta, color = Palette.warning)
                        }
                    }
                    FormRow(label = "", modifier = Modifier.clickable { vm.isEditingIdNumber = false }) {
                        Text(stringResource(R.string.settings_hide_idnumber), style = DsText.body, color = Palette.accent)
                    }
                } else {
                    FormRow(label = stringResource(R.string.settings_id_number), modifier = Modifier.clickable { vm.isEditingIdNumber = true }) {
                        val masked = if (d.idNumber.isEmpty()) "—" else "••••"
                        val maskedDescription = stringResource(R.string.settings_id_number_masked)
                        Text(
                            text = masked,
                            style = DsText.body.copy(fontFeatureSettings = "tnum"),
                            color = Palette.textSecondary,
                            modifier = Modifier.padding(end = Space.sm).clearAndSetSemanticsDescription(maskedDescription),
                        )
                        Text(stringResource(R.string.settings_edit_idnumber), style = DsText.body, color = Palette.accent)
                    }
                }
                RowDivider()
                TextRow(d.employeeNumber, { v -> vm.edit { it.copy(employeeNumber = v) } }, stringResource(R.string.settings_employee_number))
            }

            // Workplace
            SectionHeader(stringResource(R.string.settings_workplace), icon = Icons.Filled.AccountBox)
            FormCard {
                TextRow(d.workplaceName, { v -> vm.edit { it.copy(workplaceName = v) } }, stringResource(R.string.settings_workplace_name))
                RowDivider()
                TextRow(d.contractorName, { v -> vm.edit { it.copy(contractorName = v) } }, stringResource(R.string.settings_contractor))
            }

            // Pay and hours
            SectionHeader(stringResource(R.string.settings_pay_hours), icon = Icons.Filled.Info)
            FormCard {
                NumberRow(stringResource(R.string.settings_hourly_rate), d.hourlyRate, { v -> vm.edit { it.copy(hourlyRate = v) } }, unit = currencySymbol)
                RowDivider()
                NumberRow(stringResource(R.string.settings_gas_allowance), d.dailyGasAllowance, { v -> vm.edit { it.copy(dailyGasAllowance = v) } }, unit = currencySymbol)
                RowDivider()
                NumberRow(stringResource(R.string.settings_standard_hours), d.standardDayHours, { v -> vm.edit { it.copy(standardDayHours = v) } })
                RowDivider()
                NumberRow(stringResource(R.string.settings_ot_cap), d.ot125HoursCap, { v -> vm.edit { it.copy(ot125HoursCap = v) } })
                RowDivider()
                NumberRow(stringResource(R.string.settings_weekly_standard_hours), d.weeklyStandardHours, { v -> vm.edit { it.copy(weeklyStandardHours = v) } })
                RowDivider()
                NumberRow(stringResource(R.string.settings_weekly_otcap), d.weeklyOvertimeCapHours, { v -> vm.edit { it.copy(weeklyOvertimeCapHours = v) } })
            }

            // Work rules
            SectionHeader(stringResource(R.string.settings_work_rules), icon = Icons.Filled.Settings)
            FormCard {
                val weekdays = (1..7).toList()
                PickerRow(
                    label = stringResource(R.string.settings_rest_day),
                    selected = d.restDayWeekday,
                    options = weekdays,
                    optionLabel = { weekdayName(it, locale) },
                    onSelect = { v -> vm.edit { it.copy(restDayWeekday = v) } },
                )
                RowDivider()
                PickerRow(
                    label = stringResource(R.string.settings_second_rest_day),
                    selected = d.secondRestDayWeekday,
                    options = listOf(0) + weekdays,
                    optionLabel = { if (it == 0) stringResource(R.string.settings_second_rest_day_none) else weekdayName(it, locale) },
                    onSelect = { v -> vm.edit { it.copy(secondRestDayWeekday = v) } },
                )
                RowDivider()
                ToggleRow(stringResource(R.string.settings_breaks_are_paid), d.breaksArePaid, { v -> vm.edit { it.copy(breaksArePaid = v) } })
                Text(
                    text = stringResource(if (d.breaksArePaid) R.string.settings_breaks_are_paid_on_hint else R.string.settings_breaks_are_paid_off_hint),
                    style = DsText.meta,
                    color = Palette.textSecondary,
                    modifier = Modifier.padding(horizontal = Space.md, vertical = Space.xxs),
                )
                RowDivider()
                NumberRow(stringResource(R.string.settings_default_break), d.defaultBreakMinutes, { v -> vm.edit { it.copy(defaultBreakMinutes = v.filter(Char::isDigit)) } })
                RowDivider()
                var pickTime by remember { mutableStateOf(false) }
                FormRow(label = stringResource(R.string.settings_expected_shift_start), modifier = Modifier.clickable { pickTime = true }) {
                    Text(
                        text = "%02d:%02d".format(Locale.ROOT, d.expectedShiftStartHour, d.expectedShiftStartMinute),
                        style = DsText.body.copy(fontFeatureSettings = "tnum"),
                        color = Palette.accent,
                    )
                }
                if (pickTime) {
                    TimeDialog(
                        hour = d.expectedShiftStartHour,
                        minute = d.expectedShiftStartMinute,
                        onDismiss = { pickTime = false },
                        onConfirm = { h, m ->
                            pickTime = false
                            vm.edit { it.copy(expectedShiftStartHour = h, expectedShiftStartMinute = m) }
                        },
                    )
                }
                RowDivider()
                PickerRow(
                    label = stringResource(R.string.settings_currency),
                    selected = d.currencyCode,
                    options = CURRENCIES,
                    optionLabel = { "$it  ${Currency.getInstance(it).getSymbol(locale)}" },
                    onSelect = { v -> vm.edit { it.copy(currencyCode = v) } },
                )
                RowDivider()
                var noteOpen by remember { mutableStateOf(false) }
                FormRow(label = stringResource(R.string.settings_work_rules_note_title), modifier = Modifier.clickable { noteOpen = !noteOpen }) {
                    Text(if (noteOpen) "−" else "+", style = DsText.headline, color = Palette.accent)
                }
                if (noteOpen) {
                    Text(
                        text = stringResource(R.string.settings_work_rules_note),
                        style = DsText.meta,
                        color = Palette.textSecondary,
                        modifier = Modifier.padding(horizontal = Space.md, vertical = Space.xs),
                    )
                }
            }

            // Payroll cycle
            SectionHeader(stringResource(R.string.payroll_section), icon = Icons.Filled.DateRange)
            FormCard {
                Text(
                    text = stringResource(R.string.payroll_start_day_help),
                    style = DsText.meta,
                    color = Palette.textSecondary,
                    modifier = Modifier.padding(horizontal = Space.md, vertical = Space.xs),
                )
                val listState = rememberLazyListState()
                LaunchedEffect(Unit) { listState.scrollToItem((d.payrollStartDay - 1).coerceAtLeast(0)) }
                LazyRow(
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.md),
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    items((1..28).toList()) { day ->
                        val chosen = day == d.payrollStartDay
                        Text(
                            text = day.toString(),
                            style = DsText.sub.copy(fontFeatureSettings = "tnum"),
                            color = if (chosen) Palette.ink else Palette.textPrimary,
                            modifier = Modifier
                                .size(38.dp)
                                .background(if (chosen) Palette.accent else Palette.raised, CircleShape)
                                .clickable { vm.edit { it.copy(payrollStartDay = day) } }
                                .padding(top = 9.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
                val window = HistoryPeriodHelper.payrollPeriodContaining(vm.calendar.now(), d.payrollStartDay, vm.calendar)
                val short = DateTimeFormatter.ofPattern("dd/MM", locale)
                Text(
                    text = stringResource(
                        R.string.payroll_current_window,
                        "${short.format(vm.calendar.localDate(window.start))} – ${short.format(vm.calendar.localDate(window.end))}",
                    ),
                    style = DsText.meta,
                    color = Palette.textSecondary,
                    modifier = Modifier.padding(horizontal = Space.md, vertical = Space.xs),
                )
            }

            // Tax profile
            SectionHeader(stringResource(R.string.tax_section), icon = Icons.Filled.Info)
            FormCard {
                var pickDate by remember { mutableStateOf(false) }
                FormRow(label = stringResource(R.string.tax_birth_date), modifier = Modifier.clickable { pickDate = true }) {
                    Text(
                        text = d.birthDate?.let { java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(locale).format(vm.calendar.localDate(it)) } ?: "—",
                        style = DsText.body,
                        color = Palette.accent,
                    )
                }
                if (pickDate) {
                    BirthDateDialog(
                        initial = d.birthDate,
                        onDismiss = { pickDate = false },
                        onConfirm = { picked ->
                            pickDate = false
                            vm.edit { it.copy(birthDate = picked?.let { p -> vm.calendar.dateFrom(p.year, p.monthValue, p.dayOfMonth) }) }
                        },
                    )
                }
                RowDivider()
                PickerRow(
                    label = stringResource(R.string.tax_marital_status),
                    selected = d.maritalStatus,
                    options = MaritalStatus.entries,
                    optionLabel = {
                        stringResource(if (it == MaritalStatus.Single) R.string.tax_marital_single else R.string.tax_marital_married)
                    },
                    onSelect = { v -> vm.edit { it.copy(maritalStatus = v) } },
                )
                RowDivider()
                ToggleRow(stringResource(R.string.tax_has_children), d.hasChildren, { v -> vm.edit { it.copy(hasChildren = v) } })
                if (d.hasChildren) {
                    RowDivider()
                    StepperRow(
                        label = stringResource(R.string.tax_number_of_children),
                        value = d.numberOfChildren,
                        range = 0..15,
                        onValueChange = { v -> vm.edit { it.copy(numberOfChildren = v) } },
                        minusDescription = "−",
                        plusDescription = "+",
                    )
                }
                if (d.maritalStatus == MaritalStatus.Married) {
                    RowDivider()
                    ToggleRow(stringResource(R.string.tax_spouse_employed), d.spouseEmployed, { v -> vm.edit { it.copy(spouseEmployed = v) } })
                }
                RowDivider()
                FormRow(label = stringResource(R.string.tax_credit_points)) {
                    Text("%.2f".format(Locale.ROOT, vm.creditPoints), style = DsText.body.copy(fontFeatureSettings = "tnum"), color = Palette.textSecondary)
                }
                Text(
                    text = stringResource(R.string.tax_estimate_note),
                    style = DsText.meta,
                    color = Palette.textSecondary,
                    modifier = Modifier.padding(horizontal = Space.md, vertical = Space.xs),
                )
            }

            // Language
            SectionHeader(stringResource(R.string.settings_app_language))
            FormCard {
                PickerRow(
                    label = stringResource(R.string.settings_app_language),
                    selected = language,
                    options = AppLanguage.entries,
                    optionLabel = { it.label ?: stringResource(R.string.settings_language_system) },
                    onSelect = onLanguageChange,
                )
            }
            Text(
                text = stringResource(R.string.settings_app_language_hint),
                style = DsText.meta,
                color = Palette.textSecondary,
                modifier = Modifier.padding(horizontal = Space.xs, vertical = Space.xs),
            )

            // Room for the floating tab bar.
            androidx.compose.foundation.layout.Spacer(Modifier.size(120.dp))
        }
    }
}

private fun weekdayName(foundationWeekday: Int, locale: Locale): String =
    DayOfWeek.of(if (foundationWeekday == 1) 7 else foundationWeekday - 1).getDisplayName(TextStyle.FULL, locale)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("✕") } },
        text = { TimePicker(state = state) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthDateDialog(initial: Instant?, onDismiss: () -> Unit, onConfirm: (java.time.LocalDate?) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial?.toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() })
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("✕") } },
    ) { DatePicker(state = state) }
}

private fun Modifier.clearAndSetSemanticsDescription(description: String): Modifier =
    semantics { this.contentDescription = description }
