package com.hourstracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SettingsDao {
    @Query("SELECT * FROM workplace_settings ORDER BY colorIndex, id")
    abstract fun observeWorkplaces(): Flow<List<WorkplaceSettingsEntity>>

    @Query("SELECT * FROM workplace_settings ORDER BY colorIndex, id")
    abstract suspend fun workplaces(): List<WorkplaceSettingsEntity>

    @Query("SELECT * FROM leave_day ORDER BY date")
    abstract suspend fun leaveDays(): List<LeaveDayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertWorkplaces(rows: List<WorkplaceSettingsEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertLeaveDays(rows: List<LeaveDayEntity>)

    @Query("DELETE FROM workplace_settings")
    protected abstract suspend fun clearWorkplaces()

    @Query("DELETE FROM leave_day")
    protected abstract suspend fun clearLeaveDays()

    /** Replaces every workplace and leave day in one step (a restore). */
    @Transaction
    open suspend fun replaceAll(workplaces: List<WorkplaceSettingsEntity>, leaveDays: List<LeaveDayEntity>) {
        clearWorkplaces()
        clearLeaveDays()
        insertWorkplaces(workplaces)
        insertLeaveDays(leaveDays)
    }
}
