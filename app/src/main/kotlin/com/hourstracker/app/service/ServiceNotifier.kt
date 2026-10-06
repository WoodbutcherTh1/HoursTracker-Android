package com.hourstracker.app.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.hourstracker.data.ShiftRecord
import java.time.Instant

/** Starts and stops [ShiftService] for a shift. Called while the app is on screen or from a notification button. */
class ServiceNotifier(private val context: Context) : ShiftNotifier {
    override fun show(record: ShiftRecord, breaksArePaid: Boolean) {
        val session = record.session
        val now = Instant.now()
        val onBreak = session.isOnBreak
        val paused = onBreak && !breaksArePaid
        val paidElapsedMs = (session.paidElapsedSeconds(now, breaksArePaid) * 1000).toLong()
        val intent = Intent(context, ShiftService::class.java)
            .setAction(ShiftService.ACTION_SHOW)
            .putExtra(ShiftService.EXTRA_CLOCK_IN, session.clockIn.toEpochMilli())
            .putExtra(ShiftService.EXTRA_PAID_ELAPSED_MS, paidElapsedMs)
            .putExtra(ShiftService.EXTRA_ON_BREAK, onBreak)
            .putExtra(ShiftService.EXTRA_BREAK_SINCE, session.activeBreak?.start?.toEpochMilli() ?: 0L)
            .putExtra(ShiftService.EXTRA_PAUSED, paused)
        ContextCompat.startForegroundService(context, intent)
    }

    override fun hide() {
        context.startService(Intent(context, ShiftService::class.java).setAction(ShiftService.ACTION_HIDE))
    }
}
