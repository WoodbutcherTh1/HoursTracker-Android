package com.hourstracker.model.golden

import com.hourstracker.model.OvertimeCalculator
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/** Section E: `OvertimeCalculator.aggregate`, the period totals, in all three pinned environments. */
class AggregateGoldenTest {
    @TestFactory
    fun `period totals with weekly overtime`(): List<DynamicTest> = goldenTests("aggregate") { c ->
        val input = c.case["input"]!!.jsonObject
        val settings = c.settings(input["settings"]!!.jsonObject)
        val sessions = c.sessions(input["sessions"]!!.jsonArray)
        c.breakdown("output", c.case["output"]!!.jsonObject, OvertimeCalculator.aggregate(sessions, settings, c.cal))
    }
}
