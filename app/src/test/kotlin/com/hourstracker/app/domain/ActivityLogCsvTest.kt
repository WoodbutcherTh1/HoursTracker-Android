package com.hourstracker.app.domain

import com.hourstracker.data.db.AuditLogEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActivityLogCsvTest {
    private fun entry(action: String = "shift.update", metadata: String? = null, target: String? = "id-1") =
        AuditLogEntity(id = 1, timestamp = 1_781_690_400_000, actor = "user", action = action, targetId = target, beforeHash = "b".repeat(64), afterHash = "a".repeat(64), metadata = metadata)

    @Test
    fun `the first line is the header and each entry is one line`() {
        val lines = ActivityLogCsv.build(listOf(entry(), entry("shift.delete"))).removePrefix("﻿").trimEnd('\n').split("\n")
        assertEquals(ActivityLogCsv.HEADER, lines[0])
        assertEquals(3, lines.size)
    }

    @Test
    fun `the timestamp is ISO 8601 in UTC`() {
        assertTrue(ActivityLogCsv.build(listOf(entry())).contains("2026-06-17T"))
    }

    @Test
    fun `JSON with commas and quotes is quoted so the columns stay aligned`() {
        val csv = ActivityLogCsv.build(listOf(entry(metadata = """{"fields":["clockIn","notes"]}""")))
        assertTrue(csv.contains("\"{\"\"fields\"\":[\"\"clockIn\"\",\"\"notes\"\"]}\""))
    }

    @Test
    fun `a cell that starts like a formula is neutralised`() {
        assertTrue(ActivityLogCsv.build(listOf(entry(target = "=SUM(A1)"))).contains("'=SUM(A1)"))
    }

    @Test
    fun `an empty log is just the header`() {
        assertEquals(ActivityLogCsv.HEADER + "\n", ActivityLogCsv.build(emptyList()).removePrefix("﻿"))
    }
}
