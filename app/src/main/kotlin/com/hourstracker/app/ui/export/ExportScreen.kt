package com.hourstracker.app.ui.export

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.hourstracker.app.LocalAppContainer
import com.hourstracker.app.R
import com.hourstracker.app.domain.CsvExporter
import com.hourstracker.app.domain.ExportBuilder
import com.hourstracker.app.domain.ExportCopy
import com.hourstracker.app.domain.ExportDayFilter
import com.hourstracker.app.domain.ExportRange
import com.hourstracker.app.domain.ReportLanguage
import com.hourstracker.app.ui.components.FormCard
import com.hourstracker.app.ui.components.FormRow
import com.hourstracker.app.ui.components.PickerRow
import com.hourstracker.app.ui.components.PrimaryButton
import com.hourstracker.app.ui.components.RowDivider
import com.hourstracker.app.ui.components.SectionHeader
import com.hourstracker.app.ui.components.ToggleRow
import com.hourstracker.app.ui.theme.DsText
import com.hourstracker.app.ui.theme.Palette
import com.hourstracker.app.ui.theme.Space
import com.hourstracker.model.PayFormatter
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import com.hourstracker.model.HistoryPeriodHelper
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import com.hourstracker.app.ui.components.EmptyState
import com.hourstracker.app.ui.components.ErrorState
import com.hourstracker.app.ui.nav.TabIcons
import com.hourstracker.data.AuditAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class RangeMode { ThisMonth, SpecificMonth, ThisYear, Custom }

private enum class Format(val extension: String, val mime: String) { Pdf("pdf", "application/pdf"), Csv("csv", "text/csv") }

/** Builds a report for a date range and shares it as PDF or CSV. */
@Composable
fun ExportScreen() {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val calendar = remember { container.deviceCalendar() }
    val records by container.shifts.shifts.collectAsState(initial = emptyList())
    val settings by container.settings.settings.collectAsState()
    val profile by container.settings.profile.collectAsState()

    var rangeMode by rememberSaveable { mutableStateOf(RangeMode.ThisMonth) }
    val today = calendar.localDate(calendar.now())
    var monthEpoch by rememberSaveable { mutableStateOf(YearMonth.from(today).atDay(1).toEpochDay()) }
    var customFromEpoch by rememberSaveable { mutableStateOf(today.minusMonths(1).toEpochDay()) }
    var customToEpoch by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var format by rememberSaveable { mutableStateOf(Format.Pdf) }
    var dayFilter by rememberSaveable { mutableStateOf(ExportDayFilter.All) }
    var language by rememberSaveable { mutableStateOf(ReportLanguage.Phone) }
    var includeNotes by rememberSaveable { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val month = YearMonth.from(LocalDate.ofEpochDay(monthEpoch))
    val range: ExportRange = when (rangeMode) {
        RangeMode.ThisMonth -> ExportRange.ThisMonth
        RangeMode.SpecificMonth -> ExportRange.Month(month.year, month.monthValue)
        RangeMode.ThisYear -> ExportRange.ThisYear
        RangeMode.Custom -> ExportRange.Custom(LocalDate.ofEpochDay(customFromEpoch), LocalDate.ofEpochDay(customToEpoch))
    }
    val report = remember(records, settings, range, dayFilter, includeNotes) {
        ExportBuilder.build(records, settings, calendar, range, dayFilter, includeNotes)
    }
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)

    fun createReport() {
        busy = true
        failed = false
        scope.launch {
            try {
                val deviceLanguage = Locale.getDefault().language.let { if (it == "iw") "he" else it }
                val copy = ExportCopy(language.resolve(deviceLanguage))
                val idNumber = container.settings.readIdNumber()
                val file = withContext(Dispatchers.IO) {
                    val directory = File(context.cacheDir, "exports").apply { mkdirs() }
                    directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 3600 * 1000 }?.forEach { it.delete() }
                    val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm", Locale.ROOT).format(calendar.now().atZone(calendar.zone))
                    File(directory, "HoursTracker_$stamp.${format.extension}").also { out ->
                        if (format == Format.Csv) {
                            out.writeText(CsvExporter.write(report, copy, calendar), Charsets.UTF_8)
                        } else {
                            out.writeBytes(PdfReportWriter.write(report, copy, profile, idNumber, calendar))
                        }
                    }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                val send = Intent(Intent.ACTION_SEND)
                    .setType(format.mime)
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                container.audit.log(
                    AuditAction.EXPORT_REPORT,
                    metadata = mapOf("format" to format.extension, "rows" to report.rows.size, "language" to language.name, "includesNotes" to includeNotes),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Out of storage, no app to share to, or a rendering fault: say so and offer a retry.
                android.util.Log.w("Export", "Report failed", e)
                failed = true
            } finally {
                busy = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.export_title),
            style = DsText.titleScreen,
            color = Palette.textPrimary,
            modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm),
        )
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.md)) {
            SectionHeader(stringResource(R.string.export_date_range))
            FormCard {
                PickerRow(
                    label = stringResource(R.string.export_range),
                    selected = rangeMode,
                    options = RangeMode.entries,
                    optionLabel = {
                        stringResource(
                            when (it) {
                                RangeMode.ThisMonth -> R.string.export_this_month
                                RangeMode.SpecificMonth -> R.string.export_specific_month
                                RangeMode.ThisYear -> R.string.export_this_year
                                RangeMode.Custom -> R.string.export_custom_range
                            },
                        )
                    },
                    onSelect = { rangeMode = it },
                )
                if (rangeMode == RangeMode.SpecificMonth) {
                    RowDivider()
                    PickerRow(
                        label = stringResource(R.string.export_month),
                        selected = month,
                        options = (0 until 36).map { YearMonth.from(today).minusMonths(it.toLong()) },
                        optionLabel = { DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(it) },
                        onSelect = { monthEpoch = it.atDay(1).toEpochDay() },
                    )
                }
                if (rangeMode == RangeMode.Custom) {
                    RowDivider()
                    FormRow(stringResource(R.string.export_from), modifier = Modifier.clickable { pickDate = "from" }) {
                        Text(dateFormat.format(LocalDate.ofEpochDay(customFromEpoch)), style = DsText.body, color = Palette.accent)
                    }
                    RowDivider()
                    FormRow(stringResource(R.string.export_to), modifier = Modifier.clickable { pickDate = "to" }) {
                        Text(dateFormat.format(LocalDate.ofEpochDay(customToEpoch)), style = DsText.body, color = Palette.accent)
                    }
                }
                RowDivider()
                val short = DateTimeFormatter.ofPattern("dd/MM", locale)
                Text(
                    text = "${short.format(report.from)} – ${short.format(report.to)}",
                    style = DsText.meta,
                    color = Palette.textSecondary,
                    modifier = Modifier.padding(Space.md),
                )
            }

            SectionHeader(stringResource(R.string.export_format))
            FormCard {
                PickerRow(
                    label = stringResource(R.string.export_format),
                    selected = format,
                    options = Format.entries,
                    optionLabel = { stringResource(if (it == Format.Pdf) R.string.export_format_pdf else R.string.export_format_csv) },
                    onSelect = { format = it },
                )
            }

            SectionHeader(stringResource(R.string.export_day_type))
            FormCard {
                PickerRow(
                    label = stringResource(R.string.export_day_type),
                    selected = dayFilter,
                    options = ExportDayFilter.entries,
                    optionLabel = {
                        stringResource(
                            when (it) {
                                ExportDayFilter.All -> R.string.export_day_type_all
                                ExportDayFilter.Regular -> R.string.day_type_regular
                                ExportDayFilter.Holiday -> R.string.day_type_holiday
                                ExportDayFilter.Sick -> R.string.day_type_sick
                            },
                        )
                    },
                    onSelect = { dayFilter = it },
                )
            }

            SectionHeader(stringResource(R.string.export_language))
            FormCard {
                PickerRow(
                    label = stringResource(R.string.export_language),
                    selected = language,
                    options = ReportLanguage.entries,
                    optionLabel = {
                        if (it == ReportLanguage.Phone) {
                            stringResource(R.string.export_language_phone, locale.getDisplayLanguage(locale).replaceFirstChar { c -> c.titlecase(locale) })
                        } else {
                            stringResource(
                                when (it) {
                                    ReportLanguage.English -> R.string.export_language_english
                                    ReportLanguage.Hebrew -> R.string.export_language_hebrew
                                    ReportLanguage.Arabic -> R.string.export_language_arabic
                                    else -> R.string.export_language_russian
                                },
                            )
                        }
                    },
                    onSelect = { language = it },
                )
                RowDivider()
                ToggleRow(stringResource(R.string.export_include_notes), includeNotes, { includeNotes = it })
                Text(stringResource(R.string.export_include_notes_hint), style = DsText.meta, color = Palette.textSecondary, modifier = Modifier.padding(Space.md))
            }

            SectionHeader(stringResource(R.string.export_preview_title))
            FormCard {
                if (report.rows.isEmpty()) {
                    EmptyState(
                        icon = TabIcons.Export,
                        title = stringResource(R.string.export_empty_title),
                        body = stringResource(R.string.export_preview_empty),
                    )
                } else {
                    Row(modifier = Modifier.fillMaxWidth().padding(Space.md), horizontalArrangement = Arrangement.SpaceBetween) {
                        PreviewCell(stringResource(R.string.export_preview_days), report.rows.map { calendar.localDate(it.session.date) }.toSet().size.toString())
                        PreviewCell(stringResource(R.string.export_preview_hours), HistoryPeriodHelper.formatHoursClock(report.totals.totalHours))
                        PreviewCell(stringResource(R.string.export_preview_gross), PayFormatter.string(report.totals.grossPay, report.totals.currencyCode, locale))
                        PreviewCell(stringResource(R.string.export_preview_net), PayFormatter.string(report.totals.netPay, report.totals.currencyCode, locale))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Column(modifier = Modifier.navigationBarsPadding().padding(horizontal = Space.md).padding(bottom = 96.dp)) {
            if (failed) {
                ErrorState(
                    icon = TabIcons.Export,
                    title = stringResource(R.string.error_export_title),
                    body = stringResource(R.string.error_export_body),
                    actionLabel = stringResource(R.string.error_retry),
                    onAction = ::createReport,
                )
            }
            PrimaryButton(
                text = stringResource(if (busy) R.string.export_creating else R.string.export_report),
                enabled = report.rows.isNotEmpty(),
                loading = busy,
                onClick = ::createReport,
            )
        }
    }

    pickDate?.let { which ->
        ExportDateDialog(
            initial = LocalDate.ofEpochDay(if (which == "from") customFromEpoch else customToEpoch),
            onDismiss = { pickDate = null },
            onConfirm = {
                if (which == "from") customFromEpoch = it.toEpochDay() else customToEpoch = it.toEpochDay()
                pickDate = null
            },
        )
    }
}

@Composable
private fun PreviewCell(label: String, value: String) {
    Column {
        Text(label, style = DsText.meta, color = Palette.textSecondary)
        Text(value, style = DsText.headline.copy(fontFeatureSettings = "tnum"), color = Palette.textPrimary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportDateDialog(initial: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
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
