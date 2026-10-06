package com.hourstracker.model.golden

import com.hourstracker.model.PayFormatter
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.Locale

/**
 * Group K: money formatting. Apple and Android ship different locale data, so direction marks,
 * special spaces and the position of the currency symbol (for example after the number for Arabic
 * on iOS, before it on the JDK) are not compared. The digits with their separators and minus
 * sign (so rounding), and the currency symbol itself, must match exactly.
 */
class MoneyGoldenTest {
    private val ignorable = setOf(
        0x200E, 0x200F, 0x061C, 0x202A, 0x202B, 0x202C, 0x202D, 0x202E, 0x2066, 0x2067, 0x2068, 0x2069,
        0x00A0, 0x202F, 0x2009, 0x0020,
    )

    private fun normalize(text: String): Pair<String, String> {
        val kept = text.codePoints().toArray().filter { it !in ignorable }.map { String(Character.toChars(it)) }
        val number = kept.filter { it.length == 1 && (it[0] in '0'..'9' || it[0] in ",.-\u2212") }.joinToString("")
        val currency = kept.filterNot { it.length == 1 && (it[0] in '0'..'9' || it[0] in ",.-\u2212") }.joinToString("")
        return Pair(number, currency)
    }

    @TestFactory
    fun `formatting in explicit locales`(): List<DynamicTest> = goldenTests("money_locale") { c ->
        val locale = Locale.forLanguageTag(c.case.str("locale").replace('_', '-'))
        val actual = PayFormatter.string(c.case.dbl("amount"), c.case.str("currency"), locale)
        c.same("text", normalize(c.case.str("text")), normalize(actual))
    }

    @TestFactory
    fun `formatting in the process locale`(): List<DynamicTest> = goldenTests("money_env") { c ->
        val locale = Locale.forLanguageTag(c.env.systemLocale.replace('_', '-'))
        val out = c.case["output"]!!.jsonObject
        out["formatted"]!!.jsonArray.forEach { row ->
            val r = row.jsonObject
            val actual = PayFormatter.string(r.dbl("amount"), r.str("currency"), locale)
            c.same("formatted ${r.str("id")}", normalize(r.str("text")), normalize(actual))
        }
        out["symbols"]!!.jsonArray.forEach { row ->
            val r = row.jsonObject
            val actual = PayFormatter.symbol(r.str("currency"), locale)
            c.same("symbol ${r.str("currency")}", normalize(r.str("symbol")), normalize(actual))
        }
    }
}
