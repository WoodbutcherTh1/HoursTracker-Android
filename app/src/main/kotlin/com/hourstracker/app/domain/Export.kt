package com.hourstracker.app.domain

import com.hourstracker.data.ShiftRecord
import com.hourstracker.model.DayPayBreakdown
import com.hourstracker.model.DayType
import com.hourstracker.model.IosCalendar
import com.hourstracker.model.OvertimeCalculator
import com.hourstracker.model.PayFormatter
import com.hourstracker.model.WorkSession
import com.hourstracker.model.WorkplaceSettings
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** The date range of a report. */
sealed interface ExportRange {
    data object ThisMonth : ExportRange

    data class Month(val year: Int, val month: Int) : ExportRange

    data object ThisYear : ExportRange

    data class Custom(val from: LocalDate, val to: LocalDate) : ExportRange

    /** First and last calendar day (inclusive) of the range. */
    fun days(today: LocalDate): Pair<LocalDate, LocalDate> = when (this) {
        ThisMonth -> today.withDayOfMonth(1) to today.withDayOfMonth(today.lengthOfMonth())
        is Month -> LocalDate.of(year, month, 1).let { it to it.withDayOfMonth(it.lengthOfMonth()) }
        ThisYear -> LocalDate.of(today.year, 1, 1) to LocalDate.of(today.year, 12, 31)
        is Custom -> if (from <= to) from to to else to to from
    }
}

/** Which kinds of day a report includes. "Holiday" means rest days and holidays, as on iOS. */
enum class ExportDayFilter(val types: Set<DayType>?) {
    All(null),
    Regular(setOf(DayType.Regular)),
    Holiday(setOf(DayType.RestDay, DayType.Holiday)),
    Sick(setOf(DayType.Sick)),
}

/** The report languages. [code] indexes [ExportCopyTable]; Phone resolves to the app language when the report is made. */
enum class ReportLanguage(val code: String?) {
    Phone(null),
    English("en"),
    Hebrew("he"),
    Arabic("ar"),
    Russian("ru"),
    ;

    fun resolve(deviceLanguage: String): ReportLanguage = if (this != Phone) this else entries.firstOrNull { it.code == deviceLanguage } ?: English

    val isRtl: Boolean get() = this == Hebrew || this == Arabic
}

class ExportRow(val session: WorkSession, val breakdown: DayPayBreakdown, val notes: String?)

class ExportReport(
    val settings: WorkplaceSettings,
    val rows: List<ExportRow>,
    val totals: DayPayBreakdown,
    val from: LocalDate,
    val to: LocalDate,
    val includeNotes: Boolean,
)

/** Every string of a report in one language, so a report is never a mix of two. */
class ExportCopy(val language: ReportLanguage) {
    init {
        require(language != ReportLanguage.Phone) { "resolve the language first" }
    }

    val locale: Locale = Locale.forLanguageTag(language.code!!)

    private fun t(key: String): String = ExportCopyTable.strings.getValue(key).let { it[language.code] ?: it.getValue("en") }

    val title get() = t("report.title")
    val payrollSummary get() = t("report.payrollSummary")
    val dailyTable get() = t("report.dailyTable")
    val total get() = t("report.total")
    val notes get() = t("fullExport.notes")
    val colDay get() = t("report.col.day")
    val colDate get() = t("report.col.date")
    val colIn get() = t("report.col.in")
    val colOut get() = t("report.col.out")
    val colBreak get() = t("report.col.break")
    val colTotalHours get() = t("report.col.totalHours")
    val colTravel get() = t("report.col.travel")
    val colDailyWage get() = t("report.col.dailyWage")
    val summaryTotalHours get() = t("report.summary.totalHours")
    val summaryGross get() = t("report.summary.grossPay")
    val summaryNet get() = t("report.summary.netPay")
    val summaryDeductions get() = t("report.summary.deductions")

    fun worker(name: String) = t("report.worker %@").replace("%s", name)
    fun idNumber(id: String) = t("report.id %@").replace("%s", id)
    fun employee(number: String) = t("report.employee %@").replace("%s", number)
    fun workplace(name: String) = t("report.workplace %@").replace("%s", name)
    fun contractor(name: String) = t("report.contractor %@").replace("%s", name)
    fun period(text: String) = t("report.period %@").replace("%s", text)
    fun creditPoints(points: String) = t("report.creditPoints %@").replace("%s", points)

    /** Rate columns stay numeric in every language. */
    val tableColumns: List<String>
        get() = listOf(colDay, colDate, colIn, colOut, colBreak, colTotalHours, "100%", "125%", "150%", colTravel, colDailyWage)
}

object ExportBuilder {
    /** The completed shifts of [range] and [filter], priced day by day, with period totals from `aggregate`. */
    fun build(
        records: List<ShiftRecord>,
        settings: WorkplaceSettings,
        calendar: IosCalendar,
        range: ExportRange,
        filter: ExportDayFilter,
        includeNotes: Boolean,
    ): ExportReport {
        val (from, to) = range.days(calendar.localDate(calendar.now()))
        val start = from.atStartOfDay(calendar.zone).toInstant()
        val end = to.plusDays(1).atStartOfDay(calendar.zone).toInstant()
        val notesById = records.associate { it.id to it.notes }
        val completed = records.map { it.session }
            .filter { it.clockOut != null && it.date >= start && it.date < end }
            .filter { filter.types == null || it.dayType in filter.types }
        val rows = OvertimeCalculator.dayAwareBreakdowns(completed, settings, calendar)
            .map { ExportRow(it.session, it.breakdown, notesById[it.session.id]) }
        val totals = OvertimeCalculator.aggregate(completed, settings, calendar)
        return ExportReport(settings, rows, totals, from, to, includeNotes)
    }
}

/** Formats rows the way the iOS reports do: numeric dates, 24 hour times, hours with one decimal. */
class ReportFormatter(private val copy: ExportCopy, private val calendar: IosCalendar, private val currencyCode: String) {
    private val date = DateTimeFormatter.ofPattern("dd.MM.yy", Locale.ROOT)
    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    fun hours(value: Double): String = String.format(Locale.ROOT, "%.1f", value)

    fun money(value: Double): String = PayFormatter.string(value, currencyCode, copy.locale)

    fun weekday(instant: Instant): String = DayOfWeek.of(calendar.localDate(instant).dayOfWeek.value).getDisplayName(TextStyle.FULL, copy.locale)

    fun rowValues(row: ExportRow): List<String> {
        val s = row.session
        val b = row.breakdown
        return listOf(
            weekday(s.date),
            date.format(calendar.localDate(s.date)),
            time.format(s.clockIn.atZone(calendar.zone)),
            s.clockOut?.let { time.format(it.atZone(calendar.zone)) } ?: "—",
            hours(s.breakMinutes / 60.0),
            hours(b.totalHours),
            hours(b.regularHours),
            hours(b.ot125Hours),
            hours(b.ot150Hours),
            money(b.gasAllowance),
            money(b.grossPay),
        )
    }

    fun totalsValues(t: DayPayBreakdown): List<String> = listOf(
        copy.total, "", "", "", "",
        hours(t.totalHours), hours(t.regularHours), hours(t.ot125Hours), hours(t.ot150Hours),
        money(t.gasAllowance), money(t.grossPay),
    )

    /** One line per shift with a note, or empty: "Sunday 01.09.26 — note". */
    fun noteText(row: ExportRow): String? {
        val raw = row.notes ?: return null
        val oneLine = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ")
        return oneLine.ifEmpty { null }
    }

    fun rangeText(report: ExportReport): String =
        "${date.format(report.from)} – ${date.format(report.to)}"
}

object CsvExporter {
    /** U+FEFF, written as an escape so the source file itself has no byte order mark. */
    private const val BYTE_ORDER_MARK = "\uFEFF"

    /** OWASP CSV injection mitigation: neutralize formula prefixes, then quote as needed. */
    fun cell(value: String): String {
        var text = value
        if (text.isNotEmpty() && text[0] in "=+-@\t\r") text = "'$text"
        val escaped = text.replace("\"", "\"\"")
        return if (escaped.contains(',') || escaped.contains('"') || escaped.contains('\n')) "\"$escaped\"" else escaped
    }

    /** The report as CSV text (UTF-8 BOM first, so spreadsheets read Hebrew and Arabic headers correctly). */
    fun write(report: ExportReport, copy: ExportCopy, calendar: IosCalendar): String {
        val format = ReportFormatter(copy, calendar, report.totals.currencyCode)
        val withNotes = report.includeNotes
        val header = copy.tableColumns + if (withNotes) listOf(copy.notes) else emptyList()
        val lines = ArrayList<String>()
        lines += header.joinToString(",", transform = ::cell)
        report.rows.forEach { row ->
            val values = format.rowValues(row) + if (withNotes) listOf(format.noteText(row).orEmpty()) else emptyList()
            lines += values.joinToString(",", transform = ::cell)
        }
        lines += (format.totalsValues(report.totals) + if (withNotes) listOf("") else emptyList()).joinToString(",", transform = ::cell)
        return BYTE_ORDER_MARK + lines.joinToString("\n") + "\n"
    }
}
