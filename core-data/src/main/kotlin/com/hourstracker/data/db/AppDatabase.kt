package com.hourstracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Version 1 holds shifts, their breaks, workplace settings and leave days; version 2 adds the activity log.
 * Every schema is exported to `core-data/schemas`, and each change needs a migration with a test (see `MigrationTest`).
 */
@Database(
    entities = [WorkSessionEntity::class, BreakIntervalEntity::class, WorkplaceSettingsEntity::class, LeaveDayEntity::class, AuditLogEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workSessions(): WorkSessionDao

    abstract fun settings(): SettingsDao

    abstract fun auditLog(): AuditLogDao

    companion object {
        const val NAME = "hourstracker.db"

        /** 1 to 2: the activity log table. Nothing else changes, so existing shifts and settings are untouched. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `audit_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, " +
                        "`actor` TEXT NOT NULL, `action` TEXT NOT NULL, `targetId` TEXT, `beforeHash` TEXT, `afterHash` TEXT, `metadata` TEXT)",
                )
                connection.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_log_timestamp` ON `audit_log` (`timestamp`)")
            }
        }

        fun open(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME).addMigrations(MIGRATION_1_2).build()

        /** A throwaway database for tests. */
        fun inMemory(context: Context): AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }
}
