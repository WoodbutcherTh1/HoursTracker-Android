package com.hourstracker.app.ui.history

import android.icu.text.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.semantics.selected
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.hourstracker.app.R
import com.hourstracker.app.domain.HistoryCalculator
import com.hourstracker.app.domain.HistoryFilter
import com.hourstracker.app.domain.HistoryRow
import com.hourstracker.app.ui.components.EmptyState
import com.hourstracker.app.ui.components.HistorySkeleton
import com.hourstracker.app.ui.nav.TabIcons
import com.hourstracker.app.ui.theme.DsText
import com.hourstracker.app.ui.theme.Palette
import com.hourstracker.app.ui.theme.Radius
import com.hourstracker.app.ui.theme.Space
import com.hourstracker.app.ui.theme.dsCard
import com.hourstracker.app.viewModelFactory
import com.hourstracker.model.HistoryPeriodHelper
import com.hourstracker.model.PayFormatter
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale

/** The shifts of a payroll period as a table, with the period total in a bar that stays above the tab bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onAdd: () -> Unit, onEdit: (java.util.UUID) -> Unit) {
    val container = com.hourstracker.app.LocalAppContainer.current
    val vm: HistoryViewModel = viewModel(factory = viewModelFactory { HistoryViewModel(it.deviceCalendar()) })
    // null until the first read finishes, so an empty list means "no shifts" and not "still loading".
    val loadedRecords by container.shifts.shifts.collectAsState<List<com.hourstracker.data.ShiftRecord>, List<com.hourstracker.data.ShiftRecord>?>(initial = null)
    val records = loadedRecords ?: emptyList()
    val settings by container.settings.settings.collectAsState()
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val state = remember(records, settings, vm.monthOffset, vm.filter, vm.query, locale) { HistoryCalculator.build(records, settings, vm.calendar, vm.monthOffset, vm.filter, vm.query, locale) }
    val cal = vm.calendar
    val monthTitle = when (vm.filter) {
        HistoryFilter.All -> stringResource(R.string.history_filter_all_title)
        HistoryFilter.Year -> DateTimeFormatter.ofPattern("yyyy", locale).format(cal.localDate(state.period.labelMonth))
        HistoryFilter.Week -> stringResource(R.string.history_filter_week)
        else -> DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(cal.localDate(state.period.labelMonth))
    }
    val shortDay = remember(locale) { DateFormat.getInstanceForSkeleton("MMdd", locale) }
    fun day(instant: java.time.Instant) = shortDay.format(Date.from(instant))
    val timeFormat = remember(locale) {
        val pattern = java.time.format.DateTimeFormatterBuilder.getLocalizedDateTimePattern(null, FormatStyle.SHORT, java.time.chrono.IsoChronology.INSTANCE, locale)
        if ("H" in pattern && "HH" !in pattern) DateTimeFormatter.ofPattern(pattern.replace("H", "HH"), locale)
        else DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val addLabel = stringResource(R.string.history_add_shift)
    val previousLabel = stringResource(R.string.history_previous_period)
    val nextLabel = stringResource(R.string.history_next_period)
    val bulkDeletedFormat = stringResource(R.string.history_bulk_deleted)
    fun bulkDeletedMessage(count: Int) = String.format(Locale.getDefault(), bulkDeletedFormat, count)
    val cancelSelectionLabel = stringResource(R.string.history_cancel_selection)
    var confirmBulkDelete by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = vm.selecting, onBack = vm::clearSelection)
    val deletedMessage = stringResource(R.string.history_deleted)
    val undoLabel = stringResource(R.string.history_undo)
    var pendingDelete by remember { mutableStateOf<Pair<java.util.UUID, com.hourstracker.data.ShiftRecord>?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (vm.selecting) {
                Text(
                    text = "✕",
                    style = DsText.titleScreen,
                    color = Palette.textSecondary,
                    modifier = Modifier
                        .clickable(role = Role.Button, onClick = vm::clearSelection)
                        .semantics { contentDescription = cancelSelectionLabel }
                        .padding(Space.xs),
                )
                Text(
                    text = stringResource(R.string.history_selected_count, vm.selected.size),
                    style = DsText.titleSection,
                    color = Palette.textPrimary,
                    modifier = Modifier.weight(1f).padding(horizontal = Space.sm),
                )
                Text(
                    text = stringResource(R.string.history_delete_selected),
                    style = DsText.headline,
                    color = Color.White,
                    modifier = Modifier
                        .background(Color(0xFFDC2626), CircleShape)
                        .clickable(role = Role.Button) { confirmBulkDelete = true }
                        .padding(horizontal = Space.md, vertical = Space.xs),
                )
            } else {
            Text(text = stringResource(R.string.history_title), style = DsText.titleScreen, color = Palette.textPrimary)
            Text(
                text = "+",
                style = DsText.titleScreen,
                color = Palette.accent,
                modifier = Modifier
                    .background(Palette.card, CircleShape)
                    .clickable(onClickLabel = addLabel, role = Role.Button, onClick = onAdd)
                    .semantics { contentDescription = addLabel }
                    .padding(horizontal = Space.md),
            )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Space.md, vertical = Space.xs),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            HistoryFilter.entries.forEach { option ->
                Toggle(stringResource(filterLabel(option)), vm.filter == option, horizontalPadding = Space.sm) { vm.selectFilter(option) }
            }
        }
        SearchField(
            value = vm.query,
            onValueChange = vm::search,
            hint = stringResource(R.string.history_search_hint),
            clearLabel = stringResource(R.string.history_search_clear),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.xxs),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Arrows follow the reading direction: "previous" points to the start side.
            if (vm.filter == HistoryFilter.All) Spacer(Modifier.size(48.dp)) else Text(
                "‹",
                style = DsText.titleScreen,
                color = Palette.textPrimary,
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = vm::previous)
                    .semantics { contentDescription = previousLabel }
                    .padding(Space.sm),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(monthTitle, style = DsText.titleSection, color = Palette.textPrimary)
                if (vm.filter != HistoryFilter.All) {
                    Text(
                        "${day(cal.startOfDay(state.period.start))} – ${day(cal.startOfDay(state.period.end))}",
                        style = DsText.meta,
                        color = Palette.textSecondary,
                    )
                }
            }
            if (vm.filter == HistoryFilter.All) Spacer(Modifier.size(48.dp)) else Text(
                "›",
                style = DsText.titleScreen,
                color = Palette.textPrimary,
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = vm::next)
                    .semantics { contentDescription = nextLabel }
                    .padding(Space.sm),
            )
        }

        if (loadedRecords == null) {
            HistorySkeleton(modifier = Modifier.weight(1f).fillMaxWidth().padding(Space.md))
        } else if (state.rows.isEmpty()) {
            val noShiftsAtAll = records.isEmpty()
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (vm.query.isNotBlank()) {
                    EmptyState(
                        icon = TabIcons.History,
                        title = stringResource(R.string.history_search_empty_title),
                        body = stringResource(R.string.history_search_empty_body),
                    )
                } else EmptyState(
                    icon = TabIcons.History,
                    title = stringResource(if (noShiftsAtAll) R.string.history_empty else R.string.history_empty_period),
                    body = stringResource(if (noShiftsAtAll) R.string.history_empty_description else R.string.history_empty_period_hint),
                    actionLabel = if (noShiftsAtAll) stringResource(R.string.history_add_shift) else null,
                    onAction = if (noShiftsAtAll) onAdd else null,
                )
            }
        } else {
            Column(modifier = Modifier.weight(1f).padding(horizontal = Space.md, vertical = Space.sm).dsCard(Radius.lg)) {
                TableRow(
                    listOf(
                        stringResource(R.string.history_col_date),
                        stringResource(R.string.history_col_in),
                        stringResource(R.string.history_col_out),
                        stringResource(R.string.history_col_hours),
                        stringResource(R.string.history_col_amount),
                    ),
                    header = true,
                )
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.rows, key = { it.id }) { row ->
                        SwipeableRowItem(
                            row = row,
                            showNet = vm.showNet,
                            locale = locale,
                            currencyCode = state.currencyCode,
                            day = ::day,
                            timeFormat = timeFormat,
                            zone = cal.zone,
                            // A running shift is changed from Home (clock out, breaks); editing it here would close it by accident.
                            selecting = vm.selecting,
                            selected = row.id in vm.selected,
                            onClick = {
                                if (vm.selecting) vm.toggle(row.id) else if (row.clockOut != null) onEdit(row.id)
                            },
                            // A running shift cannot be deleted from here either.
                            onLongClick = { if (row.clockOut != null) vm.toggle(row.id) },
                            onDelete = { deletedRow ->
                                val record = records.find { it.id == deletedRow.id }
                                if (record != null) {
                                    pendingDelete = deletedRow.id to record
                                    scope.launch {
                                        container.shifts.delete(deletedRow.id)
                                        val result = snackbarHostState.showSnackbar(
                                            message = deletedMessage,
                                            actionLabel = undoLabel,
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            pendingDelete?.second?.let { container.shifts.upsert(it) }
                                        }
                                        pendingDelete = null
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // Total bar: net or gross, hours, always visible above the floating tab bar.
        Column(
            modifier = Modifier.fillMaxWidth().background(Palette.card).navigationBarsPadding().padding(horizontal = Space.md, vertical = Space.sm).padding(bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.CenterVertically) {
                Toggle(stringResource(R.string.history_pay_net), vm.showNet) { vm.showNet = true }
                Toggle(stringResource(R.string.history_pay_gross), !vm.showNet) { vm.showNet = false }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val total = if (vm.showNet) state.totalNet else state.totalGross
                Text(
                    stringResource(R.string.history_total_pay_value, PayFormatter.string(total, state.currencyCode, locale)),
                    style = DsText.headline,
                    color = Palette.textPrimary,
                )
                Text(
                    stringResource(R.string.history_total_hours_value, HistoryPeriodHelper.formatHoursClock(state.totalHours)),
                    style = DsText.headline.copy(fontFeatureSettings = "tnum"),
                    color = Palette.textPrimary,
                )
            }
        }
        }
        if (confirmBulkDelete) {
            AlertDialog(
                onDismissRequest = { confirmBulkDelete = false },
                title = { Text(stringResource(R.string.history_delete_selected_confirm, vm.selected.size)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmBulkDelete = false
                        val doomed = records.filter { it.id in vm.selected }
                        vm.clearSelection()
                        scope.launch {
                            doomed.forEach { container.shifts.delete(it.id) }
                            val result = snackbarHostState.showSnackbar(
                                message = bulkDeletedMessage(doomed.size),
                                actionLabel = undoLabel,
                                duration = SnackbarDuration.Long,
                            )
                            if (result == SnackbarResult.ActionPerformed) doomed.forEach { container.shifts.upsert(it) }
                        }
                    }) { Text(stringResource(R.string.history_delete), color = Palette.overdue) }
                },
                dismissButton = { TextButton(onClick = { confirmBulkDelete = false }) { Text(stringResource(R.string.edit_cancel)) } },
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp)
        )
    }
}

/** How far the row has been dragged; zero before the first layout and at rest. */
@OptIn(ExperimentalMaterial3Api::class)
private fun androidx.compose.material3.SwipeToDismissBoxState.offset(): Float =
    runCatching { requireOffset() }.getOrDefault(0f).let { if (it.isNaN()) 0f else it }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableRowItem(
    row: HistoryRow,
    showNet: Boolean,
    locale: Locale,
    currencyCode: String,
    day: (java.time.Instant) -> String,
    timeFormat: DateTimeFormatter,
    zone: java.time.ZoneId,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: (HistoryRow) -> Unit,
) {
    if (selecting) {
        // No swipe while ticking rows: the gestures would fight each other.
        Box(modifier = Modifier.background(Palette.card)) {
            RowItem(row, showNet, locale, currencyCode, day, timeFormat, zone, selected, onClick, onLongClick)
        }
        return
    }
    var dismissed by remember { mutableStateOf(false) }
    @Suppress("DEPRECATION")
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue != SwipeToDismissBoxValue.Settled && !dismissed) {
                dismissed = true
                onDelete(row)
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            // Only red while a swipe is under way; at rest the row covers it completely.
            val swiping = dismissState.targetValue != SwipeToDismissBoxValue.Settled || dismissState.offset() != 0f
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (swiping) Color.Red else Color.Transparent)
                    .padding(horizontal = Space.md),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (swiping) Text(stringResource(R.string.history_delete), color = Color.White, style = DsText.headline)
            }
        },
        content = {
            Box(modifier = Modifier.background(Palette.card)) {
                RowItem(row, showNet, locale, currencyCode, day, timeFormat, zone, false, onClick, onLongClick)
            }
        }
    )
}

private fun filterLabel(filter: HistoryFilter): Int = when (filter) {
    HistoryFilter.Week -> R.string.history_filter_week
    HistoryFilter.Month -> R.string.history_filter_month
    HistoryFilter.Payroll -> R.string.history_filter_payroll
    HistoryFilter.Year -> R.string.history_filter_year
    HistoryFilter.All -> R.string.history_filter_all
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, hint: String, clearLabel: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        placeholder = { Text(hint, style = DsText.sub, color = Palette.textTertiary) },
        textStyle = DsText.body.copy(color = Palette.textPrimary),
        trailingIcon = {
            if (value.isNotEmpty()) {
                Text(
                    text = "✕",
                    style = DsText.headline,
                    color = Palette.textSecondary,
                    modifier = Modifier
                        .clickable(role = Role.Button) { onValueChange("") }
                        .semantics { contentDescription = clearLabel }
                        .padding(Space.sm),
                )
            }
        },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.lg),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.accent,
            unfocusedBorderColor = Palette.hairline,
            focusedContainerColor = Palette.card,
            unfocusedContainerColor = Palette.card,
            cursorColor = Palette.accent,
        ),
    )
}

@Composable
private fun Toggle(label: String, selected: Boolean, horizontalPadding: androidx.compose.ui.unit.Dp = Space.md, onClick: () -> Unit) {
    Text(
        text = label,
        style = DsText.sub,
        color = if (selected) Palette.ink else Palette.textPrimary,
        modifier = Modifier
            .background(if (selected) Palette.accent else Palette.raised, CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = Space.xs),
    )
}

@Composable
private fun TableRow(cells: List<String>, header: Boolean, accentLast: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = if (header) Space.sm else Space.md)) {
        cells.forEachIndexed { index, text ->
            Text(
                text = text,
                style = (if (header) DsText.sub else DsText.callout).copy(fontFeatureSettings = "tnum"),
                color = if (header) Palette.textSecondary else if (accentLast && index == cells.lastIndex) Palette.accent else Palette.textPrimary,
                maxLines = 1,
                textAlign = if (index == 0) TextAlign.Start else if (index == cells.lastIndex) TextAlign.End else TextAlign.Center,
                modifier = Modifier.weight(if (index == 0 || index == cells.lastIndex) 1.2f else 1f),
            )
        }
    }
}

@Composable
private fun RowItem(
    row: HistoryRow,
    showNet: Boolean,
    locale: Locale,
    currencyCode: String,
    day: (java.time.Instant) -> String,
    timeFormat: DateTimeFormatter,
    zone: java.time.ZoneId,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val amount = if (showNet) row.net else row.gross
    Box(
        modifier = Modifier
            .background(if (selected) Palette.accent.copy(alpha = 0.16f) else Color.Transparent)
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
            .semantics(mergeDescendants = true) { this.selected = selected },
    ) {
        TableRow(
            listOf(
                (if (selected) "✓ " else "") + day(row.day),
                timeFormat.format(row.clockIn.atZone(zone)),
                row.clockOut?.let { timeFormat.format(it.atZone(zone)) } ?: "—",
                HistoryPeriodHelper.formatHoursClock(row.hours),
                PayFormatter.string(amount, currencyCode, locale),
            ),
            header = false,
            accentLast = true,
        )
    }
}
