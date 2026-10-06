package com.hourstracker.app.data

import android.content.Context
import com.hourstracker.app.BuildConfig
import com.hourstracker.data.RoomShiftRepository
import com.hourstracker.data.ShiftRepository
import com.hourstracker.data.db.AppDatabase
import com.hourstracker.model.IosCalendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency injection: one place that builds the long-lived objects (no Hilt). */
class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val flags = AppFlags(context)
    val settings: SettingsRepository = PrefsSettingsRepository(context, SecureIdStore(context))
    private val database = AppDatabase.open(context)
    val shifts: ShiftRepository = RoomShiftRepository(database.workSessions())

    init {
        if (BuildConfig.DEBUG) seedMockShiftsOnce()
    }

    /** `Calendar.current`: time zone, first weekday and minimal days follow the device. */
    fun deviceCalendar(): IosCalendar = IosCalendar.device()

    /** Debug builds only, once: gives History something to show for design review. Release builds start empty. */
    private fun seedMockShiftsOnce() {
        appScope.launch {
            if (flags.mockSeeded || shifts.count() > 0) return@launch
            MockShifts.build(deviceCalendar()).forEach { shifts.upsert(it) }
            flags.mockSeeded = true
        }
    }
}
