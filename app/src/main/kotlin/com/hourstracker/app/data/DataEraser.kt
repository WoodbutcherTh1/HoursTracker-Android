package com.hourstracker.app.data

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import com.hourstracker.app.service.ShiftService
import com.hourstracker.data.AuditAction
import com.hourstracker.data.AuditLog
import com.hourstracker.data.db.AppDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Delete all my data". Wipes everything this app keeps on the phone: the database (including the activity log), the
 * preferences, the ID number and its Keystore key, the cache and files, and turns off reminders and notifications.
 * Afterwards it writes ONE line to the activity log (the time, and which steps failed, nothing personal) so there is
 * proof that an erasure happened. Each step runs even if an earlier one failed; the failed steps are returned.
 *
 * Not covered, by design: files the user already shared (they left the app), and server copies. No server exists in this
 * version; when backup ships (M4) its deletion call must be added to [erase] and to `docs/COMPLIANCE_AUDIT.md`.
 */
class DataEraser(
    private val context: Context,
    private val database: AppDatabase,
    private val audit: AuditLog,
    private val idStore: SecureIdStore,
    private val cancelAlarms: () -> Unit,
) {
    suspend fun erase(): List<String> = withContext(Dispatchers.IO) {
        val failed = mutableListOf<String>()
        fun step(name: String, block: () -> Unit) {
            try {
                block()
            } catch (_: Exception) {
                failed += name
            }
        }

        step("alarms") { cancelAlarms() }
        step("notifications") {
            context.stopService(Intent(context, ShiftService::class.java))
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()
        }
        runCatching { audit.awaitIdle() } // lines still in the queue must not land after the wipe
        step("database") {
            database.clearAllTables()
            // Deleted rows can linger in free pages and the write-ahead log; compact them away.
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
            database.openHelper.writableDatabase.execSQL("VACUUM")
        }
        step("id number") { idStore.erase() }
        // commit = true on purpose: the wipe must be on disk before the app process exits.
        fun clearPreferences(name: String) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit(commit = true) { clear() }
            context.deleteSharedPreferences(name)
        }
        step("preferences") {
            PREFERENCE_FILES.forEach(::clearPreferences)
        }
        step("files") {
            listOfNotNull(context.cacheDir, context.externalCacheDir, context.filesDir).forEach(::deleteChildren)
        }

        audit.log(AuditAction.ERASURE_COMPLETED, metadata = mapOf("failedSteps" to failed.toList()))
        runCatching { audit.awaitIdle() }
        failed
    }

    private fun deleteChildren(directory: File) {
        directory.listFiles()?.forEach { it.deleteRecursively() }
    }

    companion object {
        /** Every SharedPreferences file the app creates. A new one must be added here (a test checks the list). */
        val PREFERENCE_FILES = listOf("settings", "flags", "secure_id")
    }
}
