package com.hourstracker.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hourstracker.app.data.AppContainer
import com.hourstracker.app.domain.ShiftClock
import com.hourstracker.app.domain.StatType
import com.hourstracker.data.ShiftRecord
import com.hourstracker.model.DayPayBreakdown
import com.hourstracker.model.IosCalendar
import com.hourstracker.model.LivePayCurve
import com.hourstracker.model.LivePayEngine
import com.hourstracker.model.OvertimeCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The shift just closed: what it earned and how many minutes of break were deducted. */
class DaySummary(val breakdown: DayPayBreakdown, val breakMinutes: Int)

/** What Home shows: the shifts, the running shift, its live pay curve, and the summary of the shift just closed. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val container: AppContainer) : ViewModel() {
    val calendar: IosCalendar = container.deviceCalendar()
    val settings = container.settings.settings
    val profile = container.settings.profile
    val statCardOrder = container.settings.statCardOrder
    val showNet: StateFlow<Boolean> = container.flags.showNet

    val records: StateFlow<List<ShiftRecord>> = container.shifts.shifts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val active: StateFlow<ShiftRecord?> = records.map { ShiftClock.active(it) }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * The pay of the running shift, sampled ahead by the pay engine (overtime tiers, rest-day rates, allowance, tax).
     * Rebuilt whenever the shift, a break or the settings change; Home only reads it once a second.
     */
    val curve: StateFlow<LivePayCurve?> = combine(active, settings, records) { shift, config, all -> Triple(shift, config, all) }
        .mapLatest { (shift, config, all) ->
            if (shift == null) {
                null
            } else {
                val engine = LivePayEngine(config, all.map { it.session }, calendar)
                engine.makeLivePayCurve(shift.session, calendar.now())
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val summaryFlow = MutableStateFlow<DaySummary?>(null)

    /** Set when a shift is closed; Home shows it as a sheet until it is dismissed. */
    val summary: StateFlow<DaySummary?> = summaryFlow
    private var lastClosed: ShiftRecord? = null

    fun clockIn() {
        viewModelScope.launch { container.controller.clockIn() }
    }

    fun toggleBreak() {
        viewModelScope.launch { container.controller.toggleBreak() }
    }

    fun clockOut() {
        viewModelScope.launch {
            val closed = container.controller.clockOut() ?: return@launch
            lastClosed = closed
            val sessions = records.value.map { it.session }.filter { it.clockOut != null || it.id == closed.id }
            summaryFlow.value = DaySummary(OvertimeCalculator.breakdown(closed.session, sessions, settings.value, calendar), closed.session.breakMinutes)
        }
    }

    fun dismissSummary() {
        summaryFlow.value = null
    }

    fun setShowNet(value: Boolean) = container.flags.setShowNet(value)

    fun updateStatCardOrder(newOrder: List<StatType>) {
        viewModelScope.launch {
            container.settings.saveStatCardOrder(newOrder)
        }
    }

    /** Brings the timer notification back if a shift is still running after the app was restarted. */
    suspend fun restoreNotification() = container.controller.restoreNotification()
}
