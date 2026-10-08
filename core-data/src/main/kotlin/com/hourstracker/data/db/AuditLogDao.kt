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

    /**
     * Returns how many lines were removed. Consent and erasure lines are kept however old they are: they hold no personal
     * data, and they are the proof that a person agreed to a version of the terms or that their data was deleted.
     */
    @Query("DELETE FROM audit_log WHERE timestamp < :cutoffMillis AND action NOT LIKE 'consent.%' AND action NOT LIKE 'erasure.%'")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int
}
