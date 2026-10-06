package com.hourstracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Version 1 (not released yet, so it can still change without a migration) holds shifts, their breaks, workplace settings and leave days. Schemas are exported to `core-data/schemas` for migration tests. */
@Database(
    entities = [WorkSessionEntity::class, BreakIntervalEntity::class, WorkplaceSettingsEntity::class, LeaveDayEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workSessions(): WorkSessionDao

    abstract fun settings(): SettingsDao

    companion object {
        const val NAME = "hourstracker.db"

        fun open(context: Context): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, NAME).build()

        /** A throwaway database for tests. */
        fun inMemory(context: Context): AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }
}
