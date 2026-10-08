package com.hourstracker.app.domain

import com.hourstracker.data.db.AuditLogEntity
import java.time.Instant

/** The activity log as CSV: one line per entry, timestamps in UTC, safe to open in a spreadsheet. */
object ActivityLogCsv {
    const val HEADER = "timestamp_utc,actor,action,target,before_hash,after_hash,metadata"

    fun build(entries: List<AuditLogEntity>): String = buildString {
        append('\uFEFF') // so a spreadsheet reads it as UTF-8
        append(HEADER).append('\n')
        entries.forEach { e ->
            listOf(Instant.ofEpochMilli(e.timestamp).toString(), e.actor, e.action, e.targetId.orEmpty(), e.beforeHash.orEmpty(), e.afterHash.orEmpty(), e.metadata.orEmpty())
                .joinToString(",") { CsvExporter.cell(it) }
                .let { append(it).append('\n') }
        }
    }
}
