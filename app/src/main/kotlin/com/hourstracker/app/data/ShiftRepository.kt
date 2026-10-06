package com.hourstracker.app.data

import com.hourstracker.model.WorkSession
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A stored shift: the pay-relevant [session] from core-model plus the fields the pay engine ignores.
 * `modifiedAt` and `workplaceId` exist from day one so the later Room schema needs no migration.
 */
data class ShiftRecord(
    val session: WorkSession,
    val notes: String? = null,
    val isManualEntry: Boolean = false,
    val modifiedAt: Instant = Instant.EPOCH,
    /** `null` is the main workplace. */
    val workplaceId: UUID? = null,
) {
    val id: UUID get() = session.id
}

interface ShiftRepository {
    val shifts: StateFlow<List<ShiftRecord>>

    fun upsert(record: ShiftRecord)

    fun delete(id: UUID)
}

/** Keeps shifts in memory (M2a). M2b replaces this with Room behind the same interface. */
class InMemoryShiftRepository(initial: List<ShiftRecord> = emptyList()) : ShiftRepository {
    private val flow = MutableStateFlow(initial.sortedByDescending { it.session.clockIn })

    override val shifts: StateFlow<List<ShiftRecord>> = flow.asStateFlow()

    override fun upsert(record: ShiftRecord) {
        val others = flow.value.filterNot { it.id == record.id }
        flow.value = (others + record).sortedByDescending { it.session.clockIn }
    }

    override fun delete(id: UUID) {
        flow.value = flow.value.filterNot { it.id == id }
    }
}
