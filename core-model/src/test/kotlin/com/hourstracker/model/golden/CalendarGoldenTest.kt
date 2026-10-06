package com.hourstracker.model.golden

import com.hourstracker.model.WorkSession
import com.hourstracker.model.WorkplaceSettings
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.time.Instant

/** Groups F, G and I (first part): day boundaries around daylight saving, night shifts, swapped clock times. */
class CalendarGoldenTest {
    @TestFactory
    fun `day boundaries, wall-clock times and expected shifts around daylight saving`(): List<DynamicTest> =
        goldenTests("calendar_ops") { c ->
            val out = c.case["output"]!!.jsonObject
            val start = c.cal.startOfDay(c.noon(c.case.str("day")))
            c.same("startOfDay", Instant.parse(out.str("startOfDay")), start)
            val next = c.cal.addDays(start, 1)
            c.same("nextStartOfDay", Instant.parse(out.str("nextStartOfDay")), next)
            val hours = (next.epochSecond - start.epochSecond).toDouble() / 3600
            c.bits("dayLengthHours", out["dayLengthHours"]!!, hours)

            out["setHour"]!!.jsonArray.forEach { row ->
                val r = row.jsonObject
                val actual = c.cal.setTime(start, r.optInt("hour")!!, r.optInt("minute")!!, 0)
                c.same("setHour(${r.optInt("hour")}:${r.optInt("minute")})", r.optStr("instant")?.let { Instant.parse(it) }, actual)
            }
            out["expectedShift"]!!.jsonArray.forEach { row ->
                val r = row.jsonObject
                val settings = WorkplaceSettings(
                    hourlyRate = 100.0,
                    expectedShiftStartHour = r.optInt("startHour")!!,
                    expectedShiftStartMinute = r.optInt("startMinute")!!,
                    defaultBreakMinutes = 30,
                )
                val shift = settings.expectedShift(start, c.cal)
                val label = "expectedShift(${r.optInt("startHour")}:${r.optInt("startMinute")})"
                c.same("$label.clockIn", Instant.parse(r.str("clockIn")), shift.clockIn)
                c.same("$label.clockOut", Instant.parse(r.str("clockOut")), shift.clockOut)
            }
        }

    @TestFactory
    fun `night shift qualification`(): List<DynamicTest> = goldenTests("night_shift") { c ->
        val actual = WorkSession.qualifiesAsNightShift(
            Instant.parse(c.case.str("clockIn")),
            Instant.parse(c.case.str("clockOut")),
            c.cal,
        )
        c.same("qualifies", c.case.bool("qualifies"), actual)
    }

    @TestFactory
    fun `resolving swapped clock times`(): List<DynamicTest> = goldenTests("clock_pair") { c ->
        val result = WorkSession.resolveClockPair(
            Instant.parse(c.case.str("clockIn")),
            Instant.parse(c.case.str("clockOut")),
            c.cal,
        )
        c.same("resolvedClockIn", Instant.parse(c.case.str("resolvedClockIn")), result.clockIn)
        c.same("resolvedClockOut", Instant.parse(c.case.str("resolvedClockOut")), result.clockOut)
    }
}
