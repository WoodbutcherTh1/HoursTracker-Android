package com.hourstracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Version 1 holds shifts and their breaks. Schemas are exported to `core-data/schemas` for migration tests. */
@Database(
    entities = [WorkSessionEntity::class, BreakIntervalEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workSessions(): WorkSessionDao

    companion object {
        const val NAME = "hourstracker.db"

        fun open(context: Context): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, NAME).build()

        /** A throwaway database for tests. */
        fun inMemory(context: Context): AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }
}
