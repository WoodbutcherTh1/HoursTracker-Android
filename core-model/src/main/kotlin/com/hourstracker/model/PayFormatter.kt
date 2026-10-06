package com.hourstracker.model

import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Currency-aware money formatting: two fraction digits, rounded half to even (the default of
 * Apple's `NumberFormatter`). Symbol placement, grouping and bidirectional marks come from the
 * JDK's locale data and can differ from Apple's; the golden tests compare digits, currency and
 * rounding after removing direction marks and special spaces.
 */
object PayFormatter {
    val supportedCurrencyCodes: List<String> = listOf("ILS", "USD", "EUR", "GBP")

    fun string(amount: Double, currencyCode: String, locale: Locale): String = formatter(currencyCode, locale).format(amount)

    fun symbol(currencyCode: String, locale: Locale): String = Currency.getInstance(currencyCode).getSymbol(locale)

    private fun formatter(code: String, locale: Locale): NumberFormat {
        val formatter = NumberFormat.getCurrencyInstance(locale)
        formatter.currency = Currency.getInstance(code)
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 2
        formatter.roundingMode = RoundingMode.HALF_EVEN
        return formatter
    }
}
