package com.hourstracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * One shift. All times are epoch milliseconds (UTC); the zone is applied only when a time is shown.
 *
 * The three indexes serve the History queries (by time), the per-workplace queries, and sync
 * ("what changed since"). [deletedAt] is reserved for the "recently deleted" list so that feature needs no migration.
 */
@Entity(
    tableName = "work_session",
    indices = [
        Index(value = ["clockIn"], name = "index_work_session_clockIn"),
        Index(value = ["workplaceId", "clockIn"], name = "index_work_session_workplaceId_clockIn"),
        Index(value = ["modifiedAt"], name = "index_work_session_modifiedAt"),
    ],
)
data class WorkSessionEntity(
    /** The shift id as text (a UUID), so it round-trips with the iOS backup. */
    @PrimaryKey val id: String,
    /** Start of the shift's local day, as the pay engine groups shifts by it. */
    val date: Long,
    val clockIn: Long,
    /** `null` while the shift is running. */
    val clockOut: Long?,
    val isManualEntry: Boolean,
    val isAIImported: Boolean,
    val breakMinutes: Int,
    /** `regular`, `restDay`, `holiday` or `sick`: the iOS raw values. */
    val dayType: String,
    val isNightShift: Boolean,
    val notes: String?,
    val modifiedAt: Long,
    /** `null` is the main workplace. */
    val workplaceId: String?,
    @ColumnInfo(defaultValue = "NULL") val deletedAt: Long? = null,
)

/** A live break inside a shift. `end` is `null` while the break is running. */
@Entity(
    tableName = "break_interval",
    foreignKeys = [
        ForeignKey(
            entity = WorkSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sessionId"], name = "index_break_interval_sessionId")],
)
data class BreakIntervalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val start: Long,
    val end: Long?,
)

data class SessionWithBreaks(
    @Embedded val session: WorkSessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId") val breaks: List<BreakIntervalEntity>,
)
