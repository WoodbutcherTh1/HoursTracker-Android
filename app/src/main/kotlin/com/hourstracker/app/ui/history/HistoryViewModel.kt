package com.hourstracker.app.ui.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.hourstracker.model.IosCalendar

/** Which payroll period History shows and whether amounts are net or gross. Survives rotation. */
class HistoryViewModel(val calendar: IosCalendar) : ViewModel() {
    var monthOffset: Int by mutableIntStateOf(0)
        private set

    var showNet: Boolean by mutableStateOf(true)

    fun previous() {
        monthOffset -= 1
    }

    fun next() {
        monthOffset += 1
    }
}
