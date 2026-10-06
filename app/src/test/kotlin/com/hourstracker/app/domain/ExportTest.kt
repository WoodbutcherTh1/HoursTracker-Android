package com.hourstracker.app.domain

import com.hourstracker.data.ShiftRecord
import com.hourstracker.model.DayType
import com.hourstracker.model.IosCalendar
import com.hourstracker.model.OvertimeCalculator
import com.hourstracker.model.WorkSession
import com.hourstracker.model.WorkplaceSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class ExportTest {
    private val zone = ZoneId.of("Asia/Jerusalem")
    private val now = Instant.parse("2026-06-20T09:00:00Z")
    private val calendar = IosCalendar(zone, 1, 1, Clock.fixed(now, zone))
    private val settings = WorkplaceSettings(hourlyRate = 100.0)
    private var n = 0L

    private fun shift(day: LocalDate, hours: Long = 8, type: DayType = DayType.Regular, notes: String? = null, open: Boolean = false): ShiftRecord {
        val date = day.atStartOfDay(zone).toInstant()
        val clockIn = date.plusSeconds(8 * 3600)
        return ShiftRecord(WorkSession(UUID(0, ++n), date, clockIn, if (open) null else clockIn.plusSeconds(hours * 3600), dayType = type), notes = notes)
    }

    @Test
    fun `a cell that starts like a formula is neutralized and commas are quoted`() {
        assertEquals("'=SUM(A1)", CsvExporter.cell("=SUM(A1)"))
        assertEquals("'+1", CsvExporter.cell("+1"))
        assertEquals("'@x", CsvExporter.cell("@x"))
        assertEquals("\"a,b\"", CsvExporter.cell("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExporter.cell("say \"hi\""))
        assertEquals("plain", CsvExporter.cell("plain"))
    }

    @Test
    fun `the date range covers the whole month, year or the chosen days`() {
        val today = LocalDate.of(2026, 2, 10)
        assertEquals(LocalDate.of(2026, 2, 1) to LocalDate.of(2026, 2, 28), ExportRange.ThisMonth.days(today))
        assertEquals(LocalDate.of(2028, 2, 1) to LocalDate.of(2028, 2, 29), ExportRange.Month(2028, 2).days(today))
        assertEquals(LocalDate.of(2026, 1, 1) to LocalDate.of(2026, 12, 31), ExportRange.ThisYear.days(today))
        // A custom range entered backwards is read the right way round.
        assertEquals(LocalDate.of(2026, 1, 5) to LocalDate.of(2026, 1, 9), ExportRange.Custom(LocalDate.of(2026, 1, 9), LocalDate.of(2026, 1, 5)).days(today))
    }

    @Test
    fun `the report holds only completed shifts of the range and the day filter`() {
        val records = listOf(
            shift(LocalDate.of(2026, 6, 10)),
            shift(LocalDate.of(2026, 6, 11), type = DayType.Sick),
            shift(LocalDate.of(2026, 6, 13), type = DayType.RestDay),
            shift(LocalDate.of(2026, 5, 31)),
            shift(LocalDate.of(2026, 6, 12), open = true),
        )
        fun build(filter: ExportDayFilter) = ExportBuilder.build(records, settings, calendar, ExportRange.ThisMonth, filter, includeNotes = false)
        assertEquals(3, build(ExportDayFilter.All).rows.size)
        assertEquals(1, build(ExportDayFilter.Regular).rows.size)
        assertEquals(1, build(ExportDayFilter.Holiday).rows.size) // rest days count as holidays, as on iOS
        assertEquals(1, build(ExportDayFilter.Sick).rows.size)
    }

    @Test
    fun `the totals are the same figures as the period aggregate`() {
        val records = (8..12).map { shift(LocalDate.of(2026, 6, it), hours = 9) }
        val report = ExportBuilder.build(records, settings, calendar, ExportRange.ThisMonth, ExportDayFilter.All, includeNotes = false)
        val expected = OvertimeCalculator.aggregate(records.map { it.session }, settings, calendar)
        assertEquals(expected.totalPay, report.totals.totalPay, 0.0)
        assertEquals(expected.totalHours, report.totals.totalHours, 0.0)
    }

    private fun csv(language: ReportLanguage, notes: Boolean, records: List<ShiftRecord>): List<String> {
        val report = ExportBuilder.build(records, settings, calendar, ExportRange.ThisMonth, ExportDayFilter.All, notes)
        return CsvExporter.write(report, ExportCopy(language), calendar).removePrefix("﻿").trimEnd('\n').split("\n")
    }

    @Test
    fun `the CSV starts with a byte order mark, then a header, the shifts and a totals row`() {
        val report = ExportBuilder.build(listOf(shift(LocalDate.of(2026, 6, 10))), settings, calendar, ExportRange.ThisMonth, ExportDayFilter.All, false)
        val text = CsvExporter.write(report, ExportCopy(ReportLanguage.English), calendar)
        assertTrue(text.startsWith("﻿"))
        val lines = csv(ReportLanguage.English, false, listOf(shift(LocalDate.of(2026, 6, 10))))
        assertEquals(3, lines.size)
        assertEquals(11, lines[0].split(",").size)
        assertTrue(lines[0].contains("100%") && lines[0].contains("125%") && lines[0].contains("150%"))
        assertTrue(lines[1].contains("10.06.26") && lines[1].contains("08:00") && lines[1].contains("16:00"))
        assertTrue(lines[2].startsWith(ExportCopy(ReportLanguage.English).total))
    }

    @Test
    fun `a report is entirely in one language`() {
        val records = listOf(shift(LocalDate.of(2026, 6, 10)))
        val hebrew = csv(ReportLanguage.Hebrew, false, records)[0]
        val english = csv(ReportLanguage.English, false, records)[0]
        assertTrue(hebrew.any { it in 'א'..'ת' })
        assertFalse(english.any { it in 'א'..'ת' })
        assertTrue(csv(ReportLanguage.Russian, false, records)[0].any { it in 'А'..'я' })
        assertTrue(csv(ReportLanguage.Arabic, false, records)[0].any { it in 'ء'..'ي' })
    }

    @Test
    fun `notes are added as a column only when asked, on one line`() {
        val records = listOf(shift(LocalDate.of(2026, 6, 10), notes = "covered\nfor Dana"))
        val without = csv(ReportLanguage.English, false, records)
        assertEquals(11, without[0].split(",").size)
        val with = csv(ReportLanguage.English, true, records)
        assertEquals(12, with[0].split(",").size)
        assertTrue(with[1].endsWith("covered · for Dana"))
    }

    @Test
    fun `phone language resolves to the app language and falls back to English`() {
        assertEquals(ReportLanguage.Hebrew, ReportLanguage.Phone.resolve("he"))
        assertEquals(ReportLanguage.English, ReportLanguage.Phone.resolve("fr"))
        assertEquals(ReportLanguage.Russian, ReportLanguage.Russian.resolve("he"))
        assertTrue(ReportLanguage.Hebrew.isRtl && ReportLanguage.Arabic.isRtl)
        assertFalse(ReportLanguage.English.isRtl)
    }

    @Test
    fun `every report string exists in all four languages`() {
        ExportCopyTable.strings.forEach { (key, byLanguage) ->
            assertEquals(setOf("en", "he", "ar", "ru"), byLanguage.keys, key)
            assertTrue(byLanguage.values.all { it.isNotEmpty() }, key)
        }
    }

    @Test
    fun `the overtime week is six nine hour days that trigger the weekly rule`() {
        val week = DebugScenarios.overtimeWeek(calendar)
        assertEquals(6, week.shifts.size)
        val total = OvertimeCalculator.aggregate(week.shifts.map { it.session }, settings, calendar)
        assertEquals(54.0, total.totalHours, 1e-9)
        assertEquals(42.0, total.regularHours, 1e-9) // everything above 42 hours is priced as overtime
        assertEquals(12.0, total.ot125Hours, 1e-9)
        assertEquals(0.0, total.ot150Hours, 1e-9)
    }

    @Test
    fun `the overtime week has fixed ids so loading it twice replaces it`() {
        assertEquals(DebugScenarios.overtimeWeek(calendar).shifts.map { it.id }, DebugScenarios.overtimeWeek(calendar).shifts.map { it.id })
    }

    @Test
    fun `a shift turns into form values and back to the same shift`() {
        val original = shift(LocalDate.of(2026, 6, 10), hours = 9, notes = "x")
        val input = ManualEntry.toInput(original, calendar)
        assertEquals(LocalDate.of(2026, 6, 10), input.date)
        assertEquals(8 * 60, input.clockInMinutes)
        assertEquals(17 * 60, input.clockOutMinutes)
        val rebuilt = ManualEntry.build(input, settings, calendar, now, id = original.id)
        assertEquals(original.session.clockIn, rebuilt.session.clockIn)
        assertEquals(original.session.clockOut, rebuilt.session.clockOut)
        assertEquals("x", rebuilt.notes)
    }
}
