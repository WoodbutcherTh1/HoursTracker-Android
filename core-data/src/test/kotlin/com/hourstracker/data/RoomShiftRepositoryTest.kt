package com.hourstracker.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hourstracker.data.db.AppDatabase
import com.hourstracker.model.BreakInterval
import com.hourstracker.model.DayType
import com.hourstracker.model.WorkSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class RoomShiftRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: RoomShiftRepository

    @Before
    fun open() {
        db = AppDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        repository = RoomShiftRepository(db.workSessions())
    }

    @After
    fun close() = db.close()

    private var counter = 0L

    private fun record(
        day: String = "2026-06-10",
        startHour: Int = 8,
        hours: Long = 8,
        breaks: List<BreakInterval> = emptyList(),
        dayType: DayType = DayType.Regular,
        workplace: UUID? = null,
        modified: String = "2026-06-10T20:00:00Z",
        open: Boolean = false,
        night: Boolean = false,
        notes: String? = null,
    ): ShiftRecord {
        val date = Instant.parse("${day}T00:00:00Z")
        val clockIn = date.plusSeconds(startHour * 3600L)
        return ShiftRecord(
            session = WorkSession(
                id = UUID(0, ++counter),
                date = date,
                clockIn = clockIn,
                clockOut = if (open) null else clockIn.plusSeconds(hours * 3600),
                breakMinutes = 30,
                breaks = breaks,
                dayType = dayType,
                isNightShift = night,
            ),
            notes = notes,
            isManualEntry = true,
            modifiedAt = Instant.parse(modified),
            workplaceId = workplace,
        )
    }

    private fun all(): List<ShiftRecord> = runBlocking { repository.shifts.first() }

    @Test
    fun `a shift round trips with every field`() = runBlocking {
        val workplace = UUID.randomUUID()
        val original = record(
            breaks = listOf(
                BreakInterval(Instant.parse("2026-06-10T09:00:00Z"), Instant.parse("2026-06-10T09:20:00Z")),
                BreakInterval(Instant.parse("2026-06-10T11:00:00Z"), null),
            ),
            dayType = DayType.RestDay,
            workplace = workplace,
            night = true,
            notes = "covered for Dana",
        )
        repository.upsert(original)
        assertEquals(listOf(original), all())
    }

    @Test
    fun `an open shift keeps a null clock-out`() = runBlocking {
        val open = record(open = true)
        repository.upsert(open)
        assertNull(all().single().session.clockOut)
    }

    @Test
    fun `shifts come newest first`() = runBlocking {
        val older = record(day = "2026-06-08")
        val newer = record(day = "2026-06-12")
        val middle = record(day = "2026-06-10")
        listOf(older, newer, middle).forEach { repository.upsert(it) }
        assertEquals(listOf(newer.id, middle.id, older.id), all().map { it.id })
    }

    @Test
    fun `saving a shift again replaces it and its breaks instead of adding to them`() = runBlocking {
        val first = record(breaks = listOf(BreakInterval(Instant.parse("2026-06-10T09:00:00Z"), Instant.parse("2026-06-10T09:10:00Z"))))
        repository.upsert(first)
        val changed = first.copy(notes = "edited", session = first.session.copy(breaks = emptyList()))
        repository.upsert(changed)
        val stored = all().single()
        assertEquals("edited", stored.notes)
        assertTrue(stored.session.breaks.isEmpty())
        assertEquals(0, rowCount("break_interval"))
    }

    @Test
    fun `deleting a shift deletes its breaks`() = runBlocking {
        val withBreak = record(breaks = listOf(BreakInterval(Instant.parse("2026-06-10T09:00:00Z"), null)))
        repository.upsert(withBreak)
        assertEquals(1, rowCount("break_interval"))
        repository.delete(withBreak.id)
        assertEquals(0, repository.count())
        assertEquals(0, rowCount("break_interval"))
    }

    @Test
    fun `the three indexes exist`() {
        val names = mutableListOf<String>()
        db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'work_session'").use {
            while (it.moveToNext()) names += it.getString(0)
        }
        assertTrue(names.toString(), names.containsAll(listOf("index_work_session_clockIn", "index_work_session_workplaceId_clockIn", "index_work_session_modifiedAt")))
    }

    @Test
    fun `times are stored as epoch milliseconds in UTC`() = runBlocking {
        val shift = record(startHour = 8, hours = 1)
        repository.upsert(shift)
        val clockIn = db.openHelper.readableDatabase.query("SELECT clockIn FROM work_session").use {
            it.moveToFirst()
            it.getLong(0)
        }
        assertEquals(Instant.parse("2026-06-10T08:00:00Z").toEpochMilli(), clockIn)
    }

    @Test
    fun `the range query uses clock-in and modifiedSince finds changes`() = runBlocking {
        val dao = db.workSessions()
        val early = record(day = "2026-06-01", modified = "2026-06-01T20:00:00Z")
        val late = record(day = "2026-06-20", modified = "2026-06-20T20:00:00Z")
        repository.upsert(early)
        repository.upsert(late)
        val june10 = Instant.parse("2026-06-10T00:00:00Z").toEpochMilli()
        assertEquals(listOf(late.id.toString()), dao.between(june10, june10 + 30L * 86_400_000).map { it.session.id })
        assertEquals(listOf(late.id.toString()), dao.modifiedSince(june10).map { it.session.id })
    }

    @Test
    fun `an unknown stored day type reads as a regular day`() = runBlocking {
        repository.upsert(record())
        db.openHelper.writableDatabase.execSQL("UPDATE work_session SET dayType = 'somethingNew'")
        assertEquals(DayType.Regular, all().single().session.dayType)
    }

    @Test
    fun `the flow emits again after a change`() = runBlocking {
        assertTrue(all().isEmpty())
        repository.upsert(record())
        assertEquals(1, all().size)
    }

    private fun rowCount(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
            it.moveToFirst()
            it.getInt(0)
        }
}
