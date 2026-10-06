package com.hourstracker.model

import java.time.Instant
import java.util.UUID

/**
 * Running-shift pay, precomputed with the real pay engine. Samples of cumulative pay against
 * paid seconds on the shift clock; surfaces only need the time to read it.
 *
 * [paidClockStartEpoch] is `Date.timeIntervalSince1970` of when the paid clock would have read 0.
 */
class LivePayCurve(
    val paidClockStartEpoch: Double,
    val pausedPaidSeconds: Double?,
    val points: List<Point>,
    val currencyCode: String,
    val sessionId: UUID? = null,
) {
    class Point(val paidSeconds: Double, val gross: Double, val net: Double)

    class Pay(val gross: Double, val net: Double)

    val isPaused: Boolean get() = pausedPaidSeconds != null

    /** Paid seconds on the shift clock at [epochSeconds]. */
    fun paidSeconds(epochSeconds: Double): Double = pausedPaidSeconds ?: swiftMax(0.0, epochSeconds - paidClockStartEpoch)

    /**
     * Pay at [epochSeconds]: linear between samples, flat before the first, and extended along
     * the last segment's slope past the end.
     */
    fun pay(epochSeconds: Double): Pay {
        val seconds = paidSeconds(epochSeconds)
        val first = points.firstOrNull() ?: return Pay(0.0, 0.0)
        if (seconds <= first.paidSeconds || points.size == 1) {
            return Pay(first.gross, first.net)
        }
        val upper = points.indexOfFirst { it.paidSeconds >= seconds }
        if (upper >= 0) {
            return interpolate(points[upper - 1], points[upper], seconds)
        }
        return interpolate(points[points.size - 2], points[points.size - 1], seconds)
    }

    private fun interpolate(a: Point, b: Point, seconds: Double): Pay {
        val span = b.paidSeconds - a.paidSeconds
        if (!(span > 0)) return Pay(b.gross, b.net)
        val t = (seconds - a.paidSeconds) / span
        return Pay(a.gross + (b.gross - a.gross) * t, a.net + (b.net - a.net) * t)
    }
}

/**
 * Live pay for the running shift: the part of the iOS view model that prices an open session.
 * [sessions] are the shifts of the workplace, including the open one.
 */
class LivePayEngine(
    private val settings: WorkplaceSettings,
    private val sessions: List<WorkSession>,
    private val calendar: IosCalendar,
) {
    /** Pay earned in the running shift if it ended at [now]. */
    fun liveBreakdown(session: WorkSession, now: Instant): DayPayBreakdown {
        val provisional = session.copy()
        val end = maxOf(session.clockIn, now)
        provisional.closeOpenBreak(end, now = end, deductFromPay = !settings.breaksArePaid)
        provisional.clockOut = end
        provisional.applyDefaultBreakIfNeeded(settings)
        return OvertimeCalculator.breakdown(provisional, sessions, settings, calendar)
    }

    /** Builds the live pay curve for [session] starting at [now]. */
    fun makeLivePayCurve(session: WorkSession, now: Instant): LivePayCurve {
        val paidNow = session.paidElapsedSeconds(now, settings.breaksArePaid)
        val isPaused = session.isOnBreak && !settings.breaksArePaid
        val current = liveBreakdown(session, now)
        val points = ArrayList<LivePayCurve.Point>()
        points.add(LivePayCurve.Point(paidNow, current.grossPay, current.netPay))

        if (!isPaused) {
            val steps = (LIVE_PAY_CURVE_HORIZON / LIVE_PAY_CURVE_STEP).toInt()
            for (step in 1..steps) {
                val ahead = step.toDouble() * LIVE_PAY_CURVE_STEP
                val breakdown = liveBreakdown(session, now.plusSeconds(ahead))
                points.add(LivePayCurve.Point(paidNow + ahead, breakdown.grossPay, breakdown.netPay))
            }
        }

        return LivePayCurve(
            paidClockStartEpoch = epochSeconds(now) + (-paidNow),
            pausedPaidSeconds = if (isPaused) paidNow else null,
            points = points,
            currencyCode = settings.currencyCode,
            sessionId = session.id,
        )
    }

    private fun epochSeconds(instant: Instant): Double = instant.epochSecond.toDouble() + instant.nano.toDouble() / 1e9

    companion object {
        const val LIVE_PAY_CURVE_STEP: Double = 5.0 * 60
        const val LIVE_PAY_CURVE_HORIZON: Double = 16.0 * 3600
    }
}
