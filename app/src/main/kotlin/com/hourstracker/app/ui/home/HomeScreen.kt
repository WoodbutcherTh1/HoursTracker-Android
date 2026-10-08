package com.hourstracker.app.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hourstracker.app.R
import com.hourstracker.app.domain.HomeStats
import com.hourstracker.app.ui.home.components.SparklineChart
import com.hourstracker.app.ui.theme.DsText
import com.hourstracker.app.ui.theme.Palette
import com.hourstracker.app.ui.theme.Radius
import com.hourstracker.app.ui.theme.Space
import com.hourstracker.app.ui.theme.dsCard
import com.hourstracker.app.viewModelFactory
import com.hourstracker.data.ShiftRecord
import com.hourstracker.model.HistoryPeriodHelper
import com.hourstracker.model.IosCalendar
import com.hourstracker.model.PayFormatter
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.delay

private fun Instant.secondsSince(other: Instant): Double =
    (epochSecond - other.epochSecond).toDouble() + (nano - other.nano).toDouble() / 1e9

/** Home: the door (clock in), or while a shift runs, its timer, live pay and the break and clock-out buttons. */
@Composable
fun HomeScreen() {
    val vm: HomeViewModel = viewModel(factory = viewModelFactory { HomeViewModel(it) })
    val active by vm.active.collectAsState()
    val records by vm.records.collectAsState()
    val settings by vm.settings.collectAsState()
    val profile by vm.profile.collectAsState()
    val curve by vm.curve.collectAsState()
    val showNet by vm.showNet.collectAsState()
    val summary by vm.summary.collectAsState()
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

    val shift = active
    val onBreak = shift != null && shift.session.isOnBreak
    val paused = onBreak && !settings.breaksArePaid

    // Real-time clock (always updates every second)
    val nowAlways by produceState(initialValue = Instant.now()) {
        while (true) {
            value = Instant.now()
            delay(1000 - (System.currentTimeMillis() % 1000))
        }
    }

    // Stats are based on real-time clock and records
    val stats = remember(records, nowAlways.epochSecond / 30) { HomeStats.compute(records, vm.calendar, nowAlways) }

    // Worked time in seconds (for timer and LiveCard) - updates every second based on nowAlways and break state
    val workedTimeSeconds by produceState(initialValue = 0L, shift, nowAlways, onBreak, settings.breaksArePaid) {
        val currentShift = shift ?: return@produceState
        val nowVal = nowAlways
        val elapsed = nowVal.secondsSince(currentShift.session.clockIn)
        val breakTime = if (settings.breaksArePaid) 0L else currentShift.session.recordedBreakSeconds(nowVal).toLong()
        value = (elapsed - breakTime).coerceAtLeast(0.0).toLong()
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.clockIn() }
    val context = LocalContext.current
    fun requestClockIn() {
        // Android 13+ asks before the timer notification can show. The shift starts either way.
        val needsAsk = Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        if (needsAsk) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.clockIn()
    }

    LaunchedEffect(Unit) { vm.restoreNotification() }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.md), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Greeting(vm, profile.fullName.trim().substringBefore(' '), nowAlways)
        
        // Sparkline for weekly hours
        SparklineChart(
            records = records,
            calendar = vm.calendar,
            now = nowAlways,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(vertical = Space.xs)
        )

        val shift = active
        if (shift == null) {
            // Clocked-out state: greeting, hours today/week, and Clock In button
            StatCards(stats)
            ClockInDoor(onClick = ::requestClockIn)
        } else {
            val onBreak = shift.session.isOnBreak
            val paused = onBreak && !settings.breaksArePaid
            val stateColor = if (onBreak) Palette.onBreak else Palette.clockedIn
            StatusRow(shift, stateColor, vm)
            LiveCard(
                shift = shift,
                paused = paused,
                paidElapsedSeconds = workedTimeSeconds.toDouble(),
                pay = curve?.takeIf { it.sessionId == shift.id }?.pay(epochSeconds(nowAlways)),
                rateMissing = settings.hourlyRate <= 0,
                currencyCode = settings.currencyCode,
                locale = locale,
                showNet = showNet,
                onShowNet = vm::setShowNet,
            )
            CompactStats(stats)
            BreakButton(onBreak = onBreak, paidBreaks = settings.breaksArePaid, onClick = vm::toggleBreak)
            ClockOutDoor(onClick = vm::clockOut)
        }
        // Room for the floating tab bar.
        Spacer(Modifier.height(120.dp))
    }

    summary?.let { DaySummarySheet(summary = it, showNet = showNet, onShowNet = vm::setShowNet, onDismiss = vm::dismissSummary) }
}

private fun epochSeconds(instant: Instant): Double = instant.epochSecond.toDouble() + instant.nano.toDouble() / 1e9

@Composable
private fun Greeting(vm: HomeViewModel, firstName: String, now: Instant) {
    val hour = vm.calendar.localDate(now).let { now.atZone(vm.calendar.zone).hour }
    val text = when (hour) {
        in 5..11 -> if (firstName.isEmpty()) stringResource(R.string.home_greeting_morning) else stringResource(R.string.home_greeting_morning_name, firstName)
        in 12..16 -> if (firstName.isEmpty()) stringResource(R.string.home_greeting_afternoon) else stringResource(R.string.home_greeting_afternoon_name, firstName)
        in 17..20 -> if (firstName.isEmpty()) stringResource(R.string.home_greeting_evening) else stringResource(R.string.home_greeting_evening_name, firstName)
        else -> if (firstName.isEmpty()) stringResource(R.string.home_greeting_night) else stringResource(R.string.home_greeting_night_name, firstName)
    }
    Text(text = text, style = DsText.titleScreen, color = Palette.textSecondary, modifier = Modifier.padding(top = Space.sm))
}

@Composable
private fun StatCards(stats: HomeStats) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs), modifier = Modifier.fillMaxWidth()) {
        StatCard(stringResource(R.string.home_stat_today_short), stringResource(R.string.home_stat_today), HistoryPeriodHelper.formatHoursClock(stats.todayHours), Modifier.weight(1f))
        StatCard(stringResource(R.string.home_stat_week_short), stringResource(R.string.home_stat_week), HistoryPeriodHelper.formatHoursClock(stats.weekHours), Modifier.weight(1f))
        StatCard(stringResource(R.string.home_stat_month_short), stringResource(R.string.home_stat_month), stats.monthShiftCount.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(title: String, accessibilityTitle: String, value: String, modifier: Modifier) {
    Column(
        modifier = modifier.dsCard(Radius.lg).padding(Space.md).semantics(mergeDescendants = true) { contentDescription = "$accessibilityTitle: $value" },
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Text(text = title, style = DsText.meta, color = Palette.textSecondary)
        // Numbers are always laid out left to right so digits never reorder inside Hebrew or Arabic text.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(text = value, style = DsText.numLarge, color = Palette.textPrimary, maxLines = 1)
        }
    }
}

@Composable
private fun CompactStats(stats: HomeStats) {
    Row(modifier = Modifier.fillMaxWidth().dsCard(Radius.lg).padding(Space.md), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf(
            stringResource(R.string.home_stat_today_short) to HistoryPeriodHelper.formatHoursClock(stats.todayHours),
            stringResource(R.string.home_stat_week_short) to HistoryPeriodHelper.formatHoursClock(stats.weekHours),
            stringResource(R.string.home_stat_month_short) to stats.monthShiftCount.toString(),
        ).forEach { (title, value) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = title, style = DsText.meta, color = Palette.textSecondary)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(text = value, style = DsText.headline.copy(fontFeatureSettings = "tnum"), color = Palette.textPrimary)
                }
            }
        }
    }
}

@Composable
private fun StatusRow(shift: ShiftRecord, color: Color, vm: HomeViewModel) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    val session = shift.session
    val text = session.activeBreak?.let { stringResource(R.string.home_status_break, time.format(it.start.atZone(vm.calendar.zone))) }
        ?: stringResource(R.string.home_status_working, time.format(session.clockIn.atZone(vm.calendar.zone)))
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Text(text = text, style = DsText.headline, color = Palette.textPrimary)
        }
        val startedYesterday = !vm.calendar.isSameDay(session.clockIn, Instant.now())
        if (startedYesterday) {
            Text(
                text = stringResource(R.string.home_night_started_yesterday),
                style = DsText.meta,
                color = Palette.textSecondary,
            )
        }
    }
}

@Composable
private fun LiveCard(
    shift: ShiftRecord,
    paused: Boolean,
    paidElapsedSeconds: Double,
    pay: com.hourstracker.model.LivePayCurve.Pay?,
    rateMissing: Boolean,
    currencyCode: String,
    locale: Locale,
    showNet: Boolean,
    onShowNet: (Boolean) -> Unit,
) {
    val glow = if (shift.session.isOnBreak) Palette.onBreak else Palette.clockedIn
    val total = paidElapsedSeconds.toLong().coerceAtLeast(0)
    val timer = "%02d:%02d:%02d".format(Locale.ROOT, total / 3600, (total % 3600) / 60, total % 60)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(listOf(glow.copy(alpha = 0.10f), Color.Transparent), radius = 160.dp.toPx()),
                    radius = 160.dp.toPx(),
                )
            }
            .dsCard(Radius.xl)
            .padding(Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        // An unpaid break stops the clock; say so in words, not only by dimming.
        Text(
            text = stringResource(R.string.home_timer_paused),
            style = DsText.meta,
            color = if (paused) Palette.onBreak else Color.Transparent,
        )
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                text = timer,
                style = DsText.numHero.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Light),
                color = if (paused) Palette.textPrimary.copy(alpha = 0.45f) else Palette.textPrimary,
                maxLines = 1,
            )
            if (rateMissing) {
                Text(text = stringResource(R.string.home_rate_missing), style = DsText.sub, color = Palette.textSecondary, textAlign = TextAlign.Center)
            } else {
                val amount = pay?.let { if (showNet) it.net else it.gross }
                Text(
                    text = amount?.let { PayFormatter.string(it, currencyCode, locale) } ?: "—",
                    style = DsText.numLarge,
                    color = Palette.accent,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(if (showNet) R.string.sum_note_net else R.string.sum_note_gross),
                    style = DsText.meta,
                    color = Palette.textTertiary,
                )
            }
        }
        if (!rateMissing) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                ModeChip(stringResource(R.string.pay_net), showNet) { onShowNet(true) }
                ModeChip(stringResource(R.string.pay_gross), !showNet) { onShowNet(false) }
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = DsText.sub,
        color = if (selected) Palette.ink else Palette.textPrimary,
        modifier = Modifier
            .background(if (selected) Palette.accent else Palette.raised, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.xs),
    )
}

@Composable
private fun ClockInDoor(onClick: () -> Unit) {
    Door(label = stringResource(R.string.home_clock_in), fill = Palette.accent, textColor = Palette.ink, glow = Palette.accent, onClick = onClick)
}

@Composable
private fun ClockOutDoor(onClick: () -> Unit) {
    Door(label = stringResource(R.string.home_clock_out), fill = Palette.clockedIn, textColor = Palette.ink, glow = Palette.clockedIn, onClick = onClick)
}

/** The big round button. One static glow sits behind it. */
@Composable
private fun Door(label: String, fill: Color, textColor: Color, glow: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .drawBehind {
                drawCircle(brush = Brush.radialGradient(listOf(glow.copy(alpha = 0.12f), Color.Transparent), radius = 160.dp.toPx()), radius = 160.dp.toPx())
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(168.dp)
                .background(fill, CircleShape)
                .semantics { role = Role.Button }
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = label, style = DsText.titleSection, color = textColor, textAlign = TextAlign.Center, modifier = Modifier.padding(Space.md))
        }
    }
}

@Composable
private fun BreakButton(onBreak: Boolean, paidBreaks: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
        Text(
            text = stringResource(if (onBreak) R.string.home_break_end else R.string.home_break_start),
            style = DsText.headline,
            color = if (onBreak) Palette.ink else Palette.onBreak,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(if (onBreak) Palette.onBreak else Palette.card, RoundedCornerShape(28.dp))
                .clickable(onClick = onClick)
                .padding(top = 16.dp),
        )
        Text(
            text = if (onBreak) stringResource(R.string.home_break_on_break) else stringResource(if (paidBreaks) R.string.home_break_paid else R.string.home_break_unpaid),
            style = DsText.meta,
            color = Palette.textTertiary,
        )
    }
}
