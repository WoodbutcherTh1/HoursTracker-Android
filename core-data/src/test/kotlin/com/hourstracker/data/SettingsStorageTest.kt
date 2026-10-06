package com.hourstracker.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hourstracker.data.backup.BackupJson
import com.hourstracker.data.backup.BackupStorage
import com.hourstracker.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsStorageTest {
    private lateinit var db: AppDatabase

    @Before
    fun open() {
        db = AppDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun close() = db.close()

    private val text = """
        {"settings": {"workplaceName": "Main", "workerFullName": "Alex", "workerIDNumber": "123456782", "employeeNumber": "E1",
          "hourlyRate": 55, "dailyGasAllowance": 35, "standardDayHours": 8.6, "ot125HoursCap": 2,
          "locationLatitude": 32.0853, "locationLongitude": 34.7818, "locationRadiusMeters": 150, "arrivalRemindersEnabled": true,
          "leaveDays": [{"id": "6F9619FF-8B86-D011-B42D-00C04FC964FF", "date": "2026-07-19T21:00:00Z", "kind": "recuperation"}],
          "additionalWorkplaces": [{"id": "11111111-1111-1111-1111-111111111111", "colorIndex": 2, "settings":
            {"workplaceName": "Second job", "workerFullName": "Alex", "workerIDNumber": "", "employeeNumber": "", "hourlyRate": 40,
             "dailyGasAllowance": 0, "standardDayHours": 8, "ot125HoursCap": 2, "locationRadiusMeters": 150}}]},
         "sessions": []}
    """.trimIndent()

    @Test
    fun `the main workplace, extra jobs, location and leave days are stored`() = runBlocking {
        val settings = BackupJson.parse(text).settings
        db.settings().replaceAll(BackupStorage.workplaceRows(settings), BackupStorage.leaveRows(settings))

        val rows = db.settings().workplaces()
        assertEquals(listOf("main", "11111111-1111-1111-1111-111111111111"), rows.map { it.id })
        assertEquals(32.0853, rows[0].locationLatitude!!, 0.0)
        assertEquals(true, rows[0].arrivalRemindersEnabled)
        assertNull(rows[1].locationLatitude)
        assertEquals(40.0, rows[1].hourlyRate, 0.0)
        assertEquals(2, rows[1].colorIndex)
        val leave = db.settings().leaveDays().single()
        assertEquals("recuperation", leave.kind)
    }

    @Test
    fun `the ID number is not a column of any table`() {
        val columns = mutableListOf<String>()
        db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'table'").use {
            while (it.moveToNext()) {
                val table = it.getString(0)
                if (table.startsWith("sqlite_") || table.startsWith("android_") || table.startsWith("room_")) continue
                db.openHelper.readableDatabase.query("PRAGMA table_info($table)").use { info ->
                    while (info.moveToNext()) columns += info.getString(1)
                }
            }
        }
        assertEquals(emptyList<String>(), columns.filter { it.contains("IDNumber", ignoreCase = true) || it.contains("IdNumber") })
    }

    @Test
    fun `restoring replaces what was stored before`() = runBlocking {
        val settings = BackupJson.parse(text).settings
        db.settings().replaceAll(BackupStorage.workplaceRows(settings), BackupStorage.leaveRows(settings))
        db.settings().replaceAll(BackupStorage.workplaceRows(settings.copy(additionalWorkplaces = emptyList(), leaveDays = emptyList())), emptyList())
        assertEquals(1, db.settings().workplaces().size)
        assertEquals(0, db.settings().leaveDays().size)
    }
}
