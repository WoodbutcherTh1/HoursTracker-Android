package com.hourstracker.data

import java.util.UUID
import kotlinx.coroutines.flow.first

/**
 * A [ShiftRepository] that writes every create, change and delete to the [AuditLog]. The log gets the record id, a hash
 * of the shift before and after, and the NAMES of the fields that changed: never times, notes or any other content.
 */
class AuditedShiftRepository(private val delegate: ShiftRepository, private val audit: AuditLog) : ShiftRepository by delegate {
    override suspend fun upsert(record: ShiftRecord) {
        val before = delegate.shifts.first().firstOrNull { it.id == record.id }
        delegate.upsert(record)
        audit.log(
            action = if (before == null) AuditAction.SHIFT_CREATE else AuditAction.SHIFT_UPDATE,
            targetId = record.id.toString(),
            before = before?.let(::fingerprint),
            after = fingerprint(record),
            metadata = if (before == null) mapOf("source" to if (record.isManualEntry) "manual" else "clock") else mapOf("fields" to changedFields(before, record)),
        )
    }

    override suspend fun delete(id: UUID) {
        val before = delegate.shifts.first().firstOrNull { it.id == id }
        delegate.delete(id)
        audit.log(AuditAction.SHIFT_DELETE, targetId = id.toString(), before = before?.let(::fingerprint))
    }

    companion object {
        /** A hash of everything that makes up the shift. */
        fun fingerprint(r: ShiftRecord): String {
            val s = r.session
            return AuditLog.hash(s.id, s.date, s.clockIn, s.clockOut, s.breakMinutes, s.breaks, s.dayType, s.isNightShift, r.notes, r.isManualEntry, r.isAIImported, r.workplaceId)
        }

        /** Which parts of the shift differ, by name. */
        fun changedFields(a: ShiftRecord, b: ShiftRecord): List<String> = buildList {
            if (a.session.date != b.session.date) add("date")
            if (a.session.clockIn != b.session.clockIn) add("clockIn")
            if (a.session.clockOut != b.session.clockOut) add("clockOut")
            if (a.session.breakMinutes != b.session.breakMinutes) add("breakMinutes")
            if (a.session.breaks != b.session.breaks) add("breaks")
            if (a.session.dayType != b.session.dayType) add("dayType")
            if (a.session.isNightShift != b.session.isNightShift) add("isNightShift")
            if (a.notes != b.notes) add("notes")
            if (a.workplaceId != b.workplaceId) add("workplaceId")
        }
    }
}
