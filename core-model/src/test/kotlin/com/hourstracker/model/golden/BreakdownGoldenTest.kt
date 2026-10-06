package com.hourstracker.model.golden

import com.hourstracker.model.OvertimeCalculator
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/** Groups A to C: `OvertimeCalculator` per hours total, per day, per session, and sick streaks. */
class BreakdownGoldenTest {
    @TestFactory
    fun `breakdown from a raw hours total`(): List<DynamicTest> = goldenTests("breakdown_simple") { c ->
        val input = c.case["input"]!!.jsonObject
        val actual = OvertimeCalculator.breakdown(
            totalHours = input.dbl("totalHours"),
            settings = c.settings(input["settings"]!!.jsonObject),
            calendar = c.cal,
            includeGasAllowance = input.bool("includeGasAllowance"),
        )
        c.breakdown("output", c.case["output"]!!.jsonObject, actual)
    }

    @TestFactory
    fun `day-aware breakdowns for several shifts in one day`(): List<DynamicTest> = goldenTests("breakdown_day", ::checkDayCase)

    @TestFactory
    fun `day types and sick pay streaks`(): List<DynamicTest> = goldenTests("day_types", ::checkDayCase)

    private fun checkDayCase(c: Check) {
        val input = c.case["input"]!!.jsonObject
        val settings = c.settings(input["settings"]!!.jsonObject)
        val sessions = c.sessions(input["sessions"]!!.jsonArray)
        val output = c.case["output"]!!.jsonObject

        val dayAware = OvertimeCalculator.dayAwareBreakdowns(sessions, settings, c.cal)
        val expectedDayAware = output["dayAware"]!!.jsonArray
        c.same("dayAware.size", expectedDayAware.size, dayAware.size)
        expectedDayAware.forEachIndexed { index, row ->
            val expected = row.jsonObject
            c.same("dayAware[$index].sessionId", expected.str("sessionId"), dayAware[index].session.id.toString())
            c.breakdown("dayAware[$index]", expected["breakdown"]!!.jsonObject, dayAware[index].breakdown)
        }

        val expectedPerSession = output["perSession"]!!.jsonArray
        c.same("perSession.size", expectedPerSession.size, sessions.size)
        expectedPerSession.forEachIndexed { index, row ->
            val expected = row.jsonObject
            c.same("perSession[$index].sessionId", expected.str("sessionId"), sessions[index].id.toString())
            val actual = OvertimeCalculator.breakdown(sessions[index], sessions, settings, c.cal)
            c.breakdown("perSession[$index]", expected["breakdown"]!!.jsonObject, actual)
        }

        val streaks = OvertimeCalculator.sickStreakDayNumbers(sessions, c.cal).entries
            .sortedBy { it.key }
            .map { c.localDay(it.key) to it.value }
        val expectedStreaks = output["sickStreaks"]!!.jsonArray.map { it.jsonObject.str("day") to it.jsonObject.optInt("number")!! }
        c.same("sickStreaks", expectedStreaks, streaks)
    }
}
