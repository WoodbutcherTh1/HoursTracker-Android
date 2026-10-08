package com.hourstracker.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hourstracker.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class AuditLogTest {
    private lateinit var db: AppDatabase
    private var now = Instant.parse("2026-06-17T10:00:00Z")
    private lateinit var log: AuditLog

    @Before
    fun open() {
        db = AppDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        log = AuditLog(db.auditLog(), CoroutineScope(Dispatchers.IO)) { now }
    }

    @After
    fun close() = db.close()

    @Test
    fun `lines are stored in the order they were logged`() = runBlocking {
        log.log(AuditAction.SHIFT_CREATE, targetId = "a")
        now = now.plusSeconds(1)
        log.log(AuditAction.SHIFT_UPDATE, targetId = "a")
        now = now.plusSeconds(1)
        log.log(AuditAction.SHIFT_DELETE, targetId = "a")
        log.awaitIdle()
        assertEquals(listOf("shift.delete", "shift.update", "shift.create"), log.entries().map { it.action })
    }

    @Test
    fun `a line keeps who, what, which record, both hashes and a JSON metadata object`() = runBlocking {
        log.log(AuditAction.SETTINGS_UPDATE, targetId = "settings", before = AuditLog.hash("a"), after = AuditLog.hash("b"), metadata = mapOf("fields" to listOf("hourlyRate", "idNumber"), "count" to 2), actor = AuditActor.USER)
        log.awaitIdle()
        val entry = log.entries().single()
        assertEquals("user", entry.actor)
        assertEquals("settings", entry.targetId)
        assertEquals(64, entry.beforeHash!!.length)
        assertNotEquals(entry.beforeHash, entry.afterHash)
        val metadata = JSONObject(entry.metadata!!)
        assertEquals(2, metadata.getInt("count"))
        assertEquals("idNumber", metadata.getJSONArray("fields").getString(1))
        assertEquals(now.toEpochMilli(), entry.timestamp)
    }

    @Test
    fun `a line without metadata stores none`() = runBlocking {
        log.log(AuditAction.CONSENT_ACCEPTED)
        log.awaitIdle()
        assertNull(log.entries().single().metadata)
    }

    @Test
    fun `the hash is stable, separates its parts and does not reveal the input`() {
        assertEquals(AuditLog.hash("x", 1), AuditLog.hash("x", 1))
        assertNotEquals(AuditLog.hash("ab", "c"), AuditLog.hash("a", "bc"))
        assertFalse(AuditLog.hash("123456782").contains("123456782"))
        assertTrue(AuditLog.hash("x").all { it in "0123456789abcdef" })
    }

    @Test
    fun `old lines are purged and the purge itself is logged`() = runBlocking {
        log.log(AuditAction.SHIFT_CREATE, targetId = "old")
        log.awaitIdle()
        now = now.plusSeconds(400 * 86_400L)
        log.log(AuditAction.SHIFT_CREATE, targetId = "new")
        log.awaitIdle()
        assertEquals(1, log.purgeOlderThan(365))
        log.awaitIdle()
        val actions = log.entries().map { it.action to it.targetId }
        assertEquals(listOf("audit.purge" to null, "shift.create" to "new"), actions)
    }

    @Test
    fun `purging nothing logs nothing and zero days keeps everything`() = runBlocking {
        log.log(AuditAction.SHIFT_CREATE, targetId = "a")
        log.awaitIdle()
        assertEquals(0, log.purgeOlderThan(365))
        assertEquals(0, log.purgeOlderThan(0))
        log.awaitIdle()
        assertEquals(1, log.count())
    }

    @Test
    fun `observe emits when a line arrives`() = runBlocking {
        log.log(AuditAction.EXPORT_REPORT, metadata = mapOf("format" to "pdf"))
        log.awaitIdle()
        assertEquals("export.report", log.observe().first().single().action)
    }

    @Test
    fun `categories group the actions`() {
        assertTrue(AuditAction.Category.Settings.matches("settings.update"))
        assertTrue(AuditAction.Category.Shifts.matches("shift.delete"))
        assertTrue(AuditAction.Category.Exports.matches("export.personal_data"))
        assertTrue(AuditAction.Category.Privacy.matches("erasure.completed"))
        assertTrue(AuditAction.Category.Privacy.matches("retention.cleanup"))
        assertFalse(AuditAction.Category.Shifts.matches("settings.update"))
    }
}
