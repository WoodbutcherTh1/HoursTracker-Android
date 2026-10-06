package com.hourstracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class WorkSessionDao {
    @Transaction
    @Query("SELECT * FROM work_session WHERE deletedAt IS NULL ORDER BY clockIn DESC")
    abstract fun observeAll(): Flow<List<SessionWithBreaks>>

    @Transaction
    @Query("SELECT * FROM work_session WHERE deletedAt IS NULL AND clockIn >= :fromMillis AND clockIn < :toMillis ORDER BY clockIn DESC")
    abstract suspend fun between(fromMillis: Long, toMillis: Long): List<SessionWithBreaks>

    @Transaction
    @Query("SELECT * FROM work_session WHERE id = :id")
    abstract suspend fun find(id: String): SessionWithBreaks?

    @Transaction
    @Query("SELECT * FROM work_session WHERE modifiedAt > :sinceMillis ORDER BY modifiedAt")
    abstract suspend fun modifiedSince(sinceMillis: Long): List<SessionWithBreaks>

    @Query("SELECT COUNT(*) FROM work_session WHERE deletedAt IS NULL")
    abstract suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertSession(session: WorkSessionEntity)

    @Insert
    protected abstract suspend fun insertBreaks(breaks: List<BreakIntervalEntity>)

    @Query("DELETE FROM break_interval WHERE sessionId = :sessionId")
    protected abstract suspend fun deleteBreaks(sessionId: String)

    @Query("DELETE FROM work_session WHERE id = :id")
    abstract suspend fun delete(id: String)

    /** Replaces a shift and its breaks as one change. */
    @Transaction
    open suspend fun upsert(session: WorkSessionEntity, breaks: List<BreakIntervalEntity>) {
        // REPLACE deletes the old row (and cascades to its breaks), then the new breaks are written.
        insertSession(session)
        deleteBreaks(session.id)
        insertBreaks(breaks)
    }
}
