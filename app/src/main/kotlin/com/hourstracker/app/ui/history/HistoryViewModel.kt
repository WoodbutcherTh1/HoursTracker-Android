package com.hourstracker.app.ui.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.hourstracker.app.domain.HistoryFilter
import com.hourstracker.model.IosCalendar
import java.util.UUID

/** Which payroll period History shows and whether amounts are net or gross. Survives rotation. */
class HistoryViewModel(val calendar: IosCalendar) : ViewModel() {
    var monthOffset: Int by mutableIntStateOf(0)
        private set

    /** Which stretch of time is listed; [monthOffset] counts in units of this filter. */
    var filter: HistoryFilter by mutableStateOf(HistoryFilter.Payroll)
        private set

    fun selectFilter(newFilter: HistoryFilter) {
        filter = newFilter
        monthOffset = 0
        clearSelection()
    }

    /** What is typed in the search box; empty shows everything. */
    var query: String by mutableStateOf("")
        private set

    fun search(text: String) {
        query = text
        clearSelection()
    }

    var showNet: Boolean by mutableStateOf(true)

    /** The shifts ticked for bulk delete. Selection mode is on while this is not empty. */
    var selected: Set<UUID> by mutableStateOf(emptySet())
        private set

    val selecting: Boolean get() = selected.isNotEmpty()

    fun toggle(id: UUID) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun clearSelection() {
        selected = emptySet()
    }

    fun previous() {
        monthOffset -= 1
        clearSelection()
    }

    fun next() {
        monthOffset += 1
        clearSelection()
    }
}
