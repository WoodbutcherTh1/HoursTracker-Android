package com.hourstracker.model

import java.math.BigDecimal
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

    /**
     * Apple's formatter rounds the shortest decimal text that identifies the Double (2.675 is
     * "2.675", so half-even gives 2.68), not the exact binary value (2.67499999...), which is what
     * `NumberFormat.format(Double)` would round.
     */
    fun string(amount: Double, currencyCode: String, locale: Locale): String =
        formatter(currencyCode, locale).format(BigDecimal.valueOf(amount))

    fun symbol(currencyCode: String, locale: Locale): String = Currency.getInstance(currencyCode).getSymbol(locale)

    /**
     * FALLBACK (documented, to be reviewed visually): digits are always Latin (0-9). The iOS golden data
     * shows Apple's formatter producing Latin digits for the "ar" locale, while the JDK defaults to
     * Arabic-Indic digits there. Forcing the Latin numbering system reproduces the iOS digits.
     */
    private fun latinDigits(locale: Locale): Locale = Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword("nu", "latn").build()

    private fun formatter(code: String, locale: Locale): NumberFormat {
        val formatter = NumberFormat.getCurrencyInstance(latinDigits(locale))
        formatter.currency = Currency.getInstance(code)
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 2
        formatter.roundingMode = RoundingMode.HALF_EVEN
        return formatter
    }
}
