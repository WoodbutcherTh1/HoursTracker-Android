package com.hourstracker.data

import com.hourstracker.data.db.WorkSessionDao
import com.hourstracker.data.db.breakEntities
import com.hourstracker.data.db.toEntity
import com.hourstracker.data.db.toRecord
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Shifts stored in Room. */
class RoomShiftRepository(private val dao: WorkSessionDao) : ShiftRepository {
    override val shifts: Flow<List<ShiftRecord>> = dao.observeAll().map { rows -> rows.map { it.toRecord() } }

    override suspend fun upsert(record: ShiftRecord) = dao.upsert(record.toEntity(), record.breakEntities())

    override suspend fun delete(id: UUID) = dao.delete(id.toString())

    override suspend fun count(): Int = dao.count()

    override suspend fun countDatedBefore(date: java.time.Instant): Int = dao.countFinishedBefore(date.toEpochMilli())

    override suspend fun deleteDatedBefore(date: java.time.Instant): Int = dao.deleteFinishedBefore(date.toEpochMilli())
}
