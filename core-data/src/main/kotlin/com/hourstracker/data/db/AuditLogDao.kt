package com.hourstracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditLogDao {
    @Insert
    suspend fun insert(entry: AuditLogEntity)

    /** Newest first. */
    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC, id DESC")
    fun observeAll(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC, id DESC")
    suspend fun all(): List<AuditLogEntity>

    @Query("SELECT COUNT(*) FROM audit_log")
    suspend fun count(): Int

    /** Returns how many lines were removed. */
    @Query("DELETE FROM audit_log WHERE timestamp < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int
}
