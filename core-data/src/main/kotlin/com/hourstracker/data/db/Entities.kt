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

/**
 * The settings of one workplace: row `main` for the main workplace, one row per extra job. Everything the iOS settings
 * hold has a column, including the workplace location (nullable) that arrival reminders will use later. The worker's ID
 * number is deliberately not here: it is encrypted in the Android Keystore and never stored in the database.
 */
@Entity(tableName = "workplace_settings")
data class WorkplaceSettingsEntity(
    @PrimaryKey val id: String,
    /** Index into the workplace colors; 0 for the main workplace. */
    val colorIndex: Int,
    val workplaceName: String,
    val contractorName: String?,
    val workerFullName: String,
    val employeeNumber: String,
    val hourlyRate: Double,
    val dailyGasAllowance: Double,
    val standardDayHours: Double,
    val ot125HoursCap: Double,
    val locationLatitude: Double?,
    val locationLongitude: Double?,
    val locationRadiusMeters: Double,
    val maritalStatus: String,
    val hasChildren: Boolean,
    val numberOfChildren: Int,
    val spouseEmployed: Boolean,
    val birthDate: Long?,
    val payrollStartDay: Int,
    val restDayWeekday: Int,
    val secondRestDayWeekday: Int?,
    val defaultBreakMinutes: Int,
    val breaksArePaid: Boolean,
    val nightStandardDayHours: Double,
    val weeklyStandardHours: Double,
    val weeklyOvertimeCapHours: Double,
    val currencyCode: String,
    val arrivalRemindersEnabled: Boolean,
    val expectedShiftStartHour: Int,
    val expectedShiftStartMinute: Int,
    val modifiedAt: Long,
)

/** A vacation or recuperation day marked in History. Display only: pay never reads it. */
@Entity(tableName = "leave_day", indices = [Index(value = ["date"], name = "index_leave_day_date")])
data class LeaveDayEntity(
    @PrimaryKey val id: String,
    /** Start of the marked day (epoch ms, UTC). */
    val date: Long,
    /** `vacation` or `recuperation`. */
    val kind: String,
)

/**
 * One line of the activity log: who did what, to which record, and when. It exists so the owner of the data can see
 * what changed on their phone and so a later audit has evidence.
 *
 * It never holds personal values. [targetId] is a record id, the hashes are SHA-256 of the record before and after
 * (so a change can be told apart without keeping the content), and [metadata] is a small JSON object of field names,
 * counts and formats.
 */
@Entity(tableName = "audit_log", indices = [Index(value = ["timestamp"], name = "index_audit_log_timestamp")])
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch milliseconds (UTC). */
    val timestamp: Long,
    /** `user`, `system` or `admin`. */
    val actor: String,
    /** A dotted name such as `shift.update`; see [com.hourstracker.data.AuditAction]. */
    val action: String,
    val targetId: String?,
    val beforeHash: String?,
    val afterHash: String?,
    /** A JSON object, or `null`. */
    val metadata: String?,
)
