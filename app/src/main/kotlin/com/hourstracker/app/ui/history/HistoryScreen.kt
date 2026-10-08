package com.hourstracker.app.ui.history

import android.icu.text.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
    val state = remember(records, settings, vm.monthOffset) { HistoryCalculator.build(records, settings, vm.calendar, vm.monthOffset) }
    val cal = vm.calendar
    val monthTitle = DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(cal.localDate(state.period.labelMonth))
    val shortDay = remember(locale) { DateFormat.getInstanceForSkeleton("MMdd", locale) }
    fun day(instant: java.time.Instant) = shortDay.format(Date.from(instant))
    val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val addLabel = stringResource(R.string.history_add_shift)
    val previousLabel = stringResource(R.string.history_previous_period)
    val nextLabel = stringResource(R.string.history_next_period)
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Arrows follow the reading direction: "previous" points to the start side.
            Text(
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
                Text(
                    "${day(cal.startOfDay(state.period.start))} – ${day(cal.startOfDay(state.period.end))}",
                    style = DsText.meta,
                    color = Palette.textSecondary,
                )
            }
            Text(
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
                EmptyState(
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
                            onClick = { onEdit(row.id) },
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
            modifier = Modifier.fillMaxWidth().background(Palette.card).padding(horizontal = Space.md, vertical = Space.sm).padding(bottom = 84.dp),
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
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp)
        )
    }
}

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
    onClick: () -> Unit,
    onDelete: (HistoryRow) -> Unit,
) {
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Red)
                    .padding(horizontal = Space.md),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(stringResource(R.string.history_delete), color = Color.White, style = DsText.headline)
            }
        },
        content = {
            RowItem(row, showNet, locale, currencyCode, day, timeFormat, zone, onClick)
        }
    )
}

@Composable
private fun Toggle(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = DsText.sub,
        color = if (selected) Palette.ink else Palette.textPrimary,
        modifier = Modifier
            .background(if (selected) Palette.accent else Palette.raised, CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.xs),
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
    onClick: () -> Unit,
) {
    val amount = if (showNet) row.net else row.gross
    Box(modifier = Modifier.clickable(role = Role.Button, onClick = onClick).semantics(mergeDescendants = true) {}) {
        TableRow(
            listOf(
                day(row.day),
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
