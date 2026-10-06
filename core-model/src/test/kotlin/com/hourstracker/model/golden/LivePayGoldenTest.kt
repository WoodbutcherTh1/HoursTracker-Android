package com.hourstracker.model.golden

import com.hourstracker.model.LivePayCurve
import com.hourstracker.model.LivePayEngine
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.double
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.time.Instant

/** Group H: the live pay curve built for a running shift, and reading a curve at a given time. */
class LivePayGoldenTest {
    private fun epoch(instant: Instant): Double = instant.epochSecond.toDouble() + instant.nano.toDouble() / 1e9

    private fun checkProbes(c: Check, expected: kotlinx.serialization.json.JsonArray, curve: LivePayCurve, base: Double) {
        expected.forEachIndexed { index, row ->
            val r = row.jsonObject
            val offset = r.optInt("offsetSeconds")!!
            val pay = curve.pay(base + offset.toDouble())
            c.bits("probes[$index](offset $offset).gross", r["gross"]!!, pay.gross)
            c.bits("probes[$index](offset $offset).net", r["net"]!!, pay.net)
        }
    }

    @TestFactory
    fun `curve for a running shift`(): List<DynamicTest> = goldenTests("live_pay_curve") { c ->
        val input = c.case["input"]!!.jsonObject
        val settings = c.settings(input["settings"]!!.jsonObject)
        val open = sessionFromSpec(input["openSession"]!!.jsonObject)
        val others = input["otherSessions"]!!.jsonArray.map { sessionFromSpec(it.jsonObject) }
        val now = Instant.parse(input.str("now"))
        val engine = LivePayEngine(settings, others + open, c.cal)
        val curve = engine.makeLivePayCurve(open, now)
        val out = c.case["output"]!!.jsonObject

        c.bits("paidClockStartEpoch", out["paidClockStartEpoch"]!!, curve.paidClockStartEpoch)
        c.same("pausedPaidSeconds present", out["pausedPaidSeconds"] != null, curve.pausedPaidSeconds != null)
        out["pausedPaidSeconds"]?.let { c.bits("pausedPaidSeconds", it, curve.pausedPaidSeconds!!) }
        c.same("currencyCode", out.str("currencyCode"), curve.currencyCode)

        val points = out["points"]!!.jsonArray
        c.same("points.size", points.size, curve.points.size)
        points.forEachIndexed { index, row ->
            val r = row.jsonObject
            c.bits("points[$index].paidSeconds", r["paidSeconds"]!!, curve.points[index].paidSeconds)
            c.bits("points[$index].gross", r["gross"]!!, curve.points[index].gross)
            c.bits("points[$index].net", r["net"]!!, curve.points[index].net)
        }
        checkProbes(c, out["probes"]!!.jsonArray, curve, epoch(now))
    }

    @TestFactory
    fun `reading hand-made curves`(): List<DynamicTest> = goldenTests("live_pay_curve_eval") { c ->
        val input = c.case["input"]!!.jsonObject
        val start = input.dbl("paidClockStartEpoch")
        val curve = LivePayCurve(
            paidClockStartEpoch = start,
            pausedPaidSeconds = input["pausedPaidSeconds"]?.jsonPrimitive?.double,
            points = input["points"]!!.jsonArray.map { row ->
                val r: JsonObject = row.jsonObject
                LivePayCurve.Point(r.dbl("paidSeconds"), r.dbl("gross"), r.dbl("net"))
            },
            currencyCode = "ILS",
        )
        checkProbes(c, c.case["output"]!!.jsonArray, curve, start)
    }
}
