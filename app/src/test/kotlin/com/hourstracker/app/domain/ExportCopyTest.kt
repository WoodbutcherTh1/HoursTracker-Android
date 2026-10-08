package com.hourstracker.app.domain

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Every sentence a report can print must exist in every report language; a missing key used to crash the PDF export. */
class ExportCopyTest {
    private val languages = listOf(ReportLanguage.English, ReportLanguage.Hebrew, ReportLanguage.Arabic, ReportLanguage.Russian)

    private fun everything(copy: ExportCopy): List<String> = listOf(
        copy.title, copy.payrollSummary, copy.dailyTable, copy.total, copy.notes,
        copy.colDay, copy.colDate, copy.colIn, copy.colOut, copy.colBreak, copy.colTotalHours, copy.colTravel, copy.colDailyWage,
        copy.summaryTotalHours, copy.summaryGross, copy.summaryNet, copy.summaryDeductions,
        copy.worker("W"), copy.idNumber("I"), copy.employee("E"), copy.workplace("P"), copy.contractor("C"),
        copy.period("D"), copy.creditPoints("2.25"),
    )

    @Test
    fun `every report line exists in every language`() {
        languages.forEach { language -> assertTrue(everything(ExportCopy(language)).none { it.isBlank() }, language.name) }
    }

    @Test
    fun `a filled-in line carries its value and no leftover placeholder`() {
        languages.forEach { language ->
            val copy = ExportCopy(language)
            listOf(copy.worker("Dana"), copy.period("01/06 - 30/06"), copy.creditPoints("2.25")).forEach {
                assertFalse(it.contains("%"), "${language.name}: $it")
            }
            assertTrue(copy.worker("Dana").contains("Dana"))
            assertTrue(copy.period("01/06 - 30/06").contains("01/06 - 30/06"))
        }
    }
}
