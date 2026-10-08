package com.hourstracker.data

import com.hourstracker.data.db.AuditLogDao
import com.hourstracker.data.db.AuditLogEntity
import java.security.MessageDigest
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** The things that are written to the activity log. Dotted names; the first part is the category. */
object AuditAction {
    const val SETTINGS_UPDATE = "settings.update"
    const val SHIFT_CREATE = "shift.create"
    const val SHIFT_UPDATE = "shift.update"
    const val SHIFT_DELETE = "shift.delete"
    const val EXPORT_REPORT = "export.report"
    const val EXPORT_PERSONAL_DATA = "export.personal_data"
    const val CONSENT_ACCEPTED = "consent.accepted"
    const val RETENTION_CLEANUP = "retention.cleanup"
    const val LOG_PURGE = "audit.purge"
    const val ERASURE_COMPLETED = "erasure.completed"

    /** The groups the Activity log screen can filter by. */
    enum class Category(val prefixes: List<String>) {
        Settings(listOf("settings.")),
        Shifts(listOf("shift.")),
        Exports(listOf("export.")),
        Privacy(listOf("consent.", "retention.", "audit.", "erasure.")),
        ;

        fun matches(action: String) = prefixes.any { action.startsWith(it) }
    }
}

object AuditActor {
    const val USER = "user"
    const val SYSTEM = "system"
    const val ADMIN = "admin"
}

/**
 * The activity log. Entries are written one at a time in the order they were logged, from a single background
 * writer, so callers never wait for the database and the order is kept.
 *
 * Privacy rule for every caller: **no personal values**. Log record ids, SHA-256 hashes (use [hash]), field names,
 * counts, formats. Never a name, an amount, a note or an ID number (a hash of an ID number can be guessed, so it is
 * not even hashed: log only that the field changed).
 */
class AuditLog(
    private val dao: AuditLogDao,
    scope: CoroutineScope,
    private val now: () -> Instant = Instant::now,
) {
    private sealed interface Job {
        class Write(val entry: AuditLogEntity) : Job
        class Barrier(val done: CompletableDeferred<Unit>) : Job
    }

    private val queue = Channel<Job>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (job in queue) {
                when (job) {
                    is Job.Write -> runCatching { dao.insert(job.entry) }
                    is Job.Barrier -> job.done.complete(Unit)
                }
            }
        }
    }

    /** Queues a line and returns at once. */
    fun log(
        action: String,
        targetId: String? = null,
        before: String? = null,
        after: String? = null,
        metadata: Map<String, Any?>? = null,
        actor: String = AuditActor.USER,
    ) {
        queue.trySend(Job.Write(AuditLogEntity(timestamp = now().toEpochMilli(), actor = actor, action = action, targetId = targetId, beforeHash = before, afterHash = after, metadata = metadata?.let(::json))))
    }

    /** Suspends until every line queued so far is in the database. */
    suspend fun awaitIdle() {
        val done = CompletableDeferred<Unit>()
        queue.send(Job.Barrier(done))
        done.await()
    }

    fun observe(): Flow<List<AuditLogEntity>> = dao.observeAll()

    suspend fun entries(): List<AuditLogEntity> = dao.all()

    suspend fun count(): Int = dao.count()

    /** Removes lines older than [days] days and returns how many went. Zero or negative days keeps everything. */
    suspend fun purgeOlderThan(days: Int): Int {
        if (days <= 0) return 0
        val removed = dao.deleteOlderThan(now().minusSeconds(days * 86_400L).toEpochMilli())
        if (removed > 0) log(AuditAction.LOG_PURGE, metadata = mapOf("removed" to removed, "olderThanDays" to days), actor = AuditActor.SYSTEM)
        return removed
    }

    companion object {
        /** SHA-256 of the parts, as 64 lowercase hex characters. Parts are separated so ("ab","c") differs from ("a","bc"). */
        fun hash(vararg parts: Any?): String {
            val digest = MessageDigest.getInstance("SHA-256")
            parts.forEach { digest.update(it.toString().toByteArray(Charsets.UTF_8)); digest.update(0) }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private fun json(map: Map<String, Any?>): String = JSONObject().apply {
            map.forEach { (key, value) ->
                put(key, if (value is Collection<*>) JSONArray(value.toList()) else value)
            }
        }.toString()
    }
}
