package com.hourstracker.app.domain

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The History search box. A shift matches when what was typed appears in one of its date spellings
 * (`16/06`, `06/16`, `16.06`, `2026-06-16`, the month name, the weekday name) or in its amount (net or gross, `427.78`
 * or `427,78`), so "427", "16/06", "june" and "monday" all find it. Digits and separators are compared as typed.
 */
object HistorySearch {
    private val datePatterns = listOf("dd/MM/yyyy", "dd/MM", "MM/dd", "dd.MM", "d.M", "d/M", "yyyy-MM-dd", "LLLL", "LLL", "EEEE", "EEE")

    fun matches(row: HistoryRow, query: String, zone: ZoneId, locale: Locale): Boolean {
        val needle = query.trim().lowercase(locale)
        if (needle.isEmpty()) return true
        val day = row.day.atZone(zone)
        val dates = datePatterns.map { DateTimeFormatter.ofPattern(it, locale).format(day).lowercase(locale) }
        if (dates.any { it.contains(needle) }) return true
        val amountNeedle = needle.replace(',', '.')
        if (amountNeedle.any { !(it.isDigit() || it == '.') }) return false
        return listOf(row.net, row.gross).any { String.format(Locale.ROOT, "%.2f", it).contains(amountNeedle) }
    }
}
