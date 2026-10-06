package com.hourstracker.data

import com.hourstracker.model.WorkSession
import java.time.Instant
import java.util.UUID

/**
 * A stored shift: the pay-relevant [session] from core-model plus the fields the pay engine ignores.
 */
data class ShiftRecord(
    val session: WorkSession,
    val notes: String? = null,
    val isManualEntry: Boolean = false,
    /** Imported by the timesheet scanner. */
    val isAIImported: Boolean = false,
    val modifiedAt: Instant = Instant.EPOCH,
    /** `null` is the main workplace. */
    val workplaceId: UUID? = null,
) {
    val id: UUID get() = session.id
}
