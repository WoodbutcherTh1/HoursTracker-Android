package com.hourstracker.app.data

import android.content.Context
import com.hourstracker.model.IosCalendar

/** Manual dependency injection: one place that builds the long-lived objects (no Hilt). */
class AppContainer(context: Context) {
    val flags = AppFlags(context)
    val settings: SettingsRepository = PrefsSettingsRepository(context, SecureIdStore(context))

    /** M2a: in-memory shifts seeded with mock data so History has something to show. */
    val shifts: ShiftRepository = InMemoryShiftRepository(MockShifts.build(deviceCalendar()))

    /** `Calendar.current`: time zone, first weekday and minimal days follow the device. */
    fun deviceCalendar(): IosCalendar = IosCalendar.device()
}
