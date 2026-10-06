package com.hourstracker.data

import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Where shifts live. Screens depend on this interface only, so the storage can change underneath them. */
interface ShiftRepository {
    /** Every shift, newest clock-in first. Emits again after each change. */
    val shifts: Flow<List<ShiftRecord>>

    suspend fun upsert(record: ShiftRecord)

    suspend fun delete(id: UUID)

    suspend fun count(): Int
}

/** Keeps shifts in memory. Used by tests and previews. */
class InMemoryShiftRepository(initial: List<ShiftRecord> = emptyList()) : ShiftRepository {
    private val flow = MutableStateFlow(initial)

    override val shifts: Flow<List<ShiftRecord>> = flow.map { list -> list.sortedByDescending { it.session.clockIn } }

    override suspend fun upsert(record: ShiftRecord) {
        flow.value = flow.value.filterNot { it.id == record.id } + record
    }

    override suspend fun delete(id: UUID) {
        flow.value = flow.value.filterNot { it.id == id }
    }

    override suspend fun count(): Int = flow.value.size
}
