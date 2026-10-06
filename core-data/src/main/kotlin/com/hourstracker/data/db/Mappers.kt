package com.hourstracker.data.db

import com.hourstracker.data.ShiftRecord
import com.hourstracker.model.BreakInterval
import com.hourstracker.model.DayType
import com.hourstracker.model.WorkSession
import java.time.Instant
import java.util.UUID

private fun Instant.ms(): Long = toEpochMilli()

private fun Long.instant(): Instant = Instant.ofEpochMilli(this)

internal fun ShiftRecord.toEntity(): WorkSessionEntity = WorkSessionEntity(
    id = id.toString(),
    date = session.date.ms(),
    clockIn = session.clockIn.ms(),
    clockOut = session.clockOut?.ms(),
    isManualEntry = isManualEntry,
    isAIImported = false,
    breakMinutes = session.breakMinutes,
    dayType = session.dayType.raw,
    isNightShift = session.isNightShift,
    notes = notes,
    modifiedAt = modifiedAt.ms(),
    workplaceId = workplaceId?.toString(),
)

internal fun ShiftRecord.breakEntities(): List<BreakIntervalEntity> = session.breaks.map {
    BreakIntervalEntity(sessionId = id.toString(), start = it.start.ms(), end = it.end?.ms())
}

internal fun SessionWithBreaks.toRecord(): ShiftRecord = ShiftRecord(
    session = WorkSession(
        id = UUID.fromString(session.id),
        date = session.date.instant(),
        clockIn = session.clockIn.instant(),
        clockOut = session.clockOut?.instant(),
        breakMinutes = session.breakMinutes,
        breaks = breaks.sortedBy { it.start }.map { BreakInterval(it.start.instant(), it.end?.instant()) },
        dayType = DayType.fromRaw(session.dayType) ?: DayType.Regular,
        isNightShift = session.isNightShift,
    ),
    notes = session.notes,
    isManualEntry = session.isManualEntry,
    modifiedAt = session.modifiedAt.instant(),
    workplaceId = session.workplaceId?.let(UUID::fromString),
)
