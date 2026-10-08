package com.hourstracker.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.hourstracker.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A phone that already has version 1 must get version 2 with its shifts intact. */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun `version 1 to 2 keeps the shifts and adds an empty activity log`() {
        helper.createDatabase("migration-test", 1).apply {
            execSQL(
                "INSERT INTO work_session (id, date, clockIn, clockOut, isManualEntry, isAIImported, breakMinutes, dayType, isNightShift, notes, modifiedAt, workplaceId, deletedAt) " +
                    "VALUES ('s1', 0, 1000, 2000, 0, 0, 30, 'regular', 0, 'kept', 3000, NULL, NULL)",
            )
            close()
        }
        val migrated = helper.runMigrationsAndValidate("migration-test", 2, true, AppDatabase.MIGRATION_1_2)
        migrated.query("SELECT notes FROM work_session WHERE id = 's1'").use {
            it.moveToFirst()
            assertEquals("kept", it.getString(0))
        }
        migrated.query("SELECT COUNT(*) FROM audit_log").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
    }
}
