package com.hourstracker.app.ui.export

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.hourstracker.app.data.UserProfile
import com.hourstracker.app.domain.ExportCopy
import com.hourstracker.app.domain.ExportReport
import com.hourstracker.app.domain.ReportFormatter
import com.hourstracker.model.IosCalendar
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Draws the report as an A4 landscape PDF with the platform [PdfDocument]: title, worker details, the payroll summary
 * and the daily table, paginated. In a right-to-left language the columns run right to left.
 */
object PdfReportWriter {
    private const val PAGE_WIDTH = 842
    private const val PAGE_HEIGHT = 595
    private const val MARGIN = 32f
    private const val ROW_HEIGHT = 16f

    // Relative widths of the eleven columns, in logical (left to right) order.
    private val COLUMN_WEIGHTS = floatArrayOf(1.3f, 1.0f, 0.7f, 0.7f, 0.7f, 0.9f, 0.8f, 0.8f, 0.8f, 1.1f, 1.2f)

    fun write(report: ExportReport, copy: ExportCopy, profile: UserProfile, idNumber: String, calendar: IosCalendar): ByteArray {
        val format = ReportFormatter(copy, calendar, report.totals.currencyCode)
        val document = PdfDocument()
        val rtl = copy.language.isRtl
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 9f }
        val bold = Paint(text).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val title = Paint(bold).apply { textSize = 18f }
        val headerFill = Paint().apply { color = Color.rgb(230, 238, 233) }
        val line = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.5f }

        val usable = PAGE_WIDTH - 2 * MARGIN
        val sum = COLUMN_WEIGHTS.sum()
        val widths = COLUMN_WEIGHTS.map { it / sum * usable }
        // Column order as drawn: reversed in right-to-left languages.
        val order = if (rtl) widths.indices.reversed().toList() else widths.indices.toList()

        var pageNumber = 0
        lateinit var page: PdfDocument.Page
        var y = 0f

        fun newPage() {
            if (pageNumber > 0) document.finishPage(page)
            pageNumber += 1
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            y = MARGIN
        }

        fun drawLine(value: String, paint: Paint) {
            val x = if (rtl) PAGE_WIDTH - MARGIN else MARGIN
            paint.textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
            page.canvas.drawText(value, x, y, paint)
            y += paint.textSize + 5f
        }

        fun drawRow(values: List<String>, paint: Paint, fill: Paint? = null) {
            val canvas = page.canvas
            if (fill != null) canvas.drawRect(MARGIN, y - ROW_HEIGHT + 4f, PAGE_WIDTH - MARGIN, y + 4f, fill)
            paint.textAlign = Paint.Align.CENTER
            var x = MARGIN
            order.forEach { logical ->
                val width = widths[logical]
                canvas.drawText(values[logical].take(24), x + width / 2, y, paint)
                x += width
            }
            canvas.drawLine(MARGIN, y + 4f, PAGE_WIDTH - MARGIN, y + 4f, line)
            y += ROW_HEIGHT
        }

        newPage()
        drawLine(copy.title, title)
        y += 4f
        drawLine(copy.period(format.rangeText(report)), text)
        if (profile.fullName.isNotBlank()) drawLine(copy.worker(profile.fullName), text)
        if (idNumber.isNotBlank()) drawLine(copy.idNumber(idNumber), text)
        if (profile.employeeNumber.isNotBlank()) drawLine(copy.employee(profile.employeeNumber), text)
        if (profile.workplaceName.isNotBlank()) drawLine(copy.workplace(profile.workplaceName), text)
        if (profile.contractorName.isNotBlank()) drawLine(copy.contractor(profile.contractorName), text)
        drawLine(copy.creditPoints(String.format(Locale.ROOT, "%.2f", report.settings.creditPoints)), text)
        y += 6f

        drawLine(copy.payrollSummary, bold)
        val totals = report.totals
        val deductions = totals.incomeTax + totals.nationalInsurance + totals.healthTax
        drawLine("${copy.summaryTotalHours}: ${format.hours(totals.totalHours)}", text)
        drawLine("${copy.summaryGross}: ${format.money(totals.grossPay)}", text)
        drawLine("${copy.summaryNet}: ${format.money(totals.netPay)}", text)
        drawLine("${copy.summaryDeductions}: ${format.money(deductions)}", text)
        y += 8f

        drawLine(copy.dailyTable, bold)
        drawRow(copy.tableColumns, bold, headerFill)
        report.rows.forEach { row ->
            if (y > PAGE_HEIGHT - MARGIN - 2 * ROW_HEIGHT) {
                newPage()
                drawRow(copy.tableColumns, bold, headerFill)
            }
            drawRow(format.rowValues(row), text)
        }
        drawRow(format.totalsValues(totals), bold, headerFill)

        if (report.includeNotes) {
            val notes = report.rows.mapNotNull { row -> format.noteText(row)?.let { "${format.weekday(row.session.date)} ${format.rowValues(row)[1]} — $it" } }
            if (notes.isNotEmpty()) {
                y += 10f
                if (y > PAGE_HEIGHT - MARGIN - 3 * ROW_HEIGHT) newPage()
                drawLine(copy.notes, bold)
                notes.forEach {
                    if (y > PAGE_HEIGHT - MARGIN) newPage()
                    drawLine(it, text)
                }
            }
        }
        document.finishPage(page)
        val out = ByteArrayOutputStream()
        try {
            document.writeTo(out)
        } finally {
            // Closing twice is harmless on a device but some test doubles close the document in writeTo.
            runCatching { document.close() }
        }
        return out.toByteArray()
    }
}
