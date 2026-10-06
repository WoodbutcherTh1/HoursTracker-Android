package com.hourstracker.model.golden

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.time.Instant

/** Group I (breaks): live breaks, the default break, and paid time elapsed. */
class BreakGoldenTest {
    @TestFactory
    fun `recording breaks and folding them into break minutes`(): List<DynamicTest> = goldenTests("break_rounding") { c ->
        val input = c.case["input"]!!.jsonObject
        val session = sessionFromSpec(input["session"]!!.jsonObject)
        val action = input["action"]!!.jsonObject
        val at = Instant.parse(action.str("at"))
        val deduct = action.bool("deductFromPay")
        val out = c.case["output"]!!.jsonObject

        // Every break in these cases is closed by the action itself, so the clock never matters.
        val returned: Boolean? = when (val kind = action.str("kind")) {
            "startBreak" -> session.startBreak(at)
            "endBreak" -> session.endBreak(at, Instant.now(), deduct)
            "closeOpenBreak" -> {
                session.closeOpenBreak(at, Instant.now(), deduct)
                null
            }
            else -> error("unknown action $kind")
        }
        c.same("returned", out["returned"]?.let { out.bool("returned") }, returned)
        c.same("breakMinutes", out.optInt("breakMinutes"), session.breakMinutes)
        val actualBreaks = session.breaks.map { Pair(it.start, it.end) }
        val expectedBreaks = out["breaks"]!!.jsonArray.map { row ->
            val spec = breakFromSpec(row.jsonObject)
            Pair(spec.start, spec.end)
        }
        c.same("breaks", expectedBreaks, actualBreaks)
    }

    @TestFactory
    fun `default break`(): List<DynamicTest> = goldenTests("default_break") { c ->
        val input = c.case["input"]!!.jsonObject
        val session = sessionFromSpec(input["session"]!!.jsonObject)
        val settings = settingsFromSpec(
            kotlinx.serialization.json.JsonObject(
                mapOf(
                    "defaultBreakMinutes" to input["defaultBreakMinutes"]!!,
                    "breaksArePaid" to input["breaksArePaid"]!!,
                ),
            ),
            c.cal,
        )
        session.applyDefaultBreakIfNeeded(settings)
        c.same("breakMinutes", c.case.optInt("breakMinutes"), session.breakMinutes)
    }

    @TestFactory
    fun `paid time elapsed and effective hours`(): List<DynamicTest> = goldenTests("paid_elapsed") { c ->
        val input = c.case["input"]!!.jsonObject
        val session = sessionFromSpec(input["session"]!!.jsonObject)
        val now = Instant.parse(input.str("now"))
        val paid = input.bool("breaksArePaid")
        val out = c.case["output"]!!.jsonObject
        c.bits("paidElapsedSeconds", out["paidElapsedSeconds"]!!, session.paidElapsedSeconds(now, paid))
        c.bits("recordedBreakSeconds", out["recordedBreakSeconds"]!!, session.recordedBreakSeconds(now))
        c.bits("totalHours", out["totalHours"]!!, session.totalHours)
        c.bits("effectiveHours", out["effectiveHours"]!!, session.effectiveHours)
    }
}
