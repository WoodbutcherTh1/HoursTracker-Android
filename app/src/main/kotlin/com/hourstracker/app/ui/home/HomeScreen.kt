package com.hourstracker.app.ui.home

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hourstracker.app.ui.components.NoticeBanner
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hourstracker.app.R
import com.hourstracker.app.domain.HomeStats
import com.hourstracker.app.domain.StatType
import com.hourstracker.app.domain.StatTypeMetadata
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
    val statCardOrder by vm.statCardOrder.collectAsState()
    val curve by vm.curve.collectAsState()
    val showNet by vm.showNet.collectAsState()
    val summary by vm.summary.collectAsState()
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

    val shift = active
    val onBreak = shift != null && shift.session.isOnBreak
    val paused = onBreak && !settings.breaksArePaid

    // Real-time clock (always updates every second)
    val nowAlways by produceState(initialValue = vm.calendar.now()) {
        while (true) {
            value = vm.calendar.now()
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

    val haptics = LocalHapticFeedback.current
    var notificationsAllowed by remember { mutableStateOf(true) }
    // Re-check on every return to the app: the worker may have just turned them on in system settings.
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose { }
    }

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

        if (shift != null && !notificationsAllowed) {
            NoticeBanner(
                text = stringResource(R.string.error_notifications_banner),
                actionLabel = stringResource(R.string.error_open_settings),
                onAction = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                },
            )
        }

        // Clocking in or out cross-fades between the two layouts. Keyed on "is a shift running" so
        // the per-second and per-break updates inside one layout do not animate.
        AnimatedContent(
            targetState = shift,
            contentKey = { it == null },
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "home-mode",
        ) { current ->
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                if (current == null) {
                    // Clocked-out state: greeting, hours today/week, and Clock In button
                    StatCards(stats, statCardOrder, vm::updateStatCardOrder)
                    ClockInDoor(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        requestClockIn()
                    })
                } else {
                    val onBreak = current.session.isOnBreak
                    val paused = onBreak && !settings.breaksArePaid
                    val stateColor = if (onBreak) Palette.onBreak else Palette.clockedIn
                    StatusRow(current, stateColor, vm)
                    LiveCard(
                        shift = current,
                        paused = paused,
                        paidElapsedSeconds = workedTimeSeconds.toDouble(),
                        pay = curve?.takeIf { it.sessionId == current.id }?.pay(epochSeconds(nowAlways)),
                        rateMissing = settings.hourlyRate <= 0,
                        currencyCode = settings.currencyCode,
                        locale = locale,
                        showNet = showNet,
                        onShowNet = vm::setShowNet,
                    )
                    CompactStats(stats)
                    BreakButton(
                        onBreak = onBreak,
                        paidBreaks = settings.breaksArePaid,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            vm.toggleBreak()
                        },
                    )
                    ClockOutDoor(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.clockOut()
                    })
                }
            }
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
private fun StatCards(stats: HomeStats, statCardOrder: List<StatType>, onStatCardOrderChanged: (List<StatType>) -> Unit) {
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var targetIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var slot by remember { mutableFloatStateOf(0f) }
    val spacingPx = with(LocalDensity.current) { Space.xs.toPx() }
    // In Hebrew and Arabic the row is mirrored, so a drag to the right moves a card toward the start.
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f

    Row(
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        modifier = Modifier.fillMaxWidth()
    ) {
        statCardOrder.forEachIndexed { index, statType ->
            val isDragging = draggedIndex == index
            val isTarget = targetIndex == index && !isDragging

            // The dragged card follows the finger; the cards it passes slide one slot out of the way.
            val targetShift = when {
                isDragging -> dragOffsetX
                draggedIndex in 0 until targetIndex && index in (draggedIndex + 1)..targetIndex -> -slot * direction
                draggedIndex > targetIndex && targetIndex >= 0 && index in targetIndex until draggedIndex -> slot * direction
                else -> 0f
            }
            val offsetX by animateFloatAsState(targetShift, spring(stiffness = Spring.StiffnessMediumLow), label = "stat-slide")

            Box(
                modifier = Modifier
                    .weight(1f)
                    .onSizeChanged { slot = it.width.toFloat() + spacingPx }
                    .offset { androidx.compose.ui.unit.IntOffset(if (isDragging) dragOffsetX.toInt() else offsetX.toInt(), 0) }
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        alpha = if (isDragging) 0.85f else 1f
                        scaleX = if (isDragging) 1.04f else 1f
                        scaleY = if (isDragging) 1.04f else 1f
                    }
                    .pointerInput(statCardOrder) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggedIndex = index
                                targetIndex = index
                                dragOffsetX = 0f
                            },
                            onDrag = { _, dragAmount ->
                                dragOffsetX += dragAmount.x
                                // Which slot the dragged card is over, counted in the reading direction.
                                val moved = if (slot > 0f) Math.round(dragOffsetX * direction / slot) else 0
                                targetIndex = (index + moved).coerceIn(0, statCardOrder.size - 1)
                            },
                            onDragEnd = {
                                if (draggedIndex != targetIndex && draggedIndex >= 0 && targetIndex >= 0) {
                                    val newOrder = statCardOrder.toMutableList()
                                    val item = newOrder.removeAt(draggedIndex)
                                    newOrder.add(targetIndex, item)
                                    onStatCardOrderChanged(newOrder)
                                }
                                draggedIndex = -1
                                targetIndex = -1
                                dragOffsetX = 0f
                            },
                            onDragCancel = {
                                draggedIndex = -1
                                targetIndex = -1
                                dragOffsetX = 0f
                            }
                        )
                    }
            ) {
                StatCard(
                    title = stringResource(StatTypeMetadata.titleResId(statType)),
                    accessibilityTitle = stringResource(StatTypeMetadata.accessibilityTitleResId(statType)),
                    value = StatTypeMetadata.formatValue(statType, stats),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
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
        val startedYesterday = !vm.calendar.isSameDay(session.clockIn, vm.calendar.now())
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
    var isFocused by remember { mutableStateOf(false) }
    val focusRingAlpha by animateFloatAsState(if (isFocused) 1f else 0f, label = "chip-focus-ring")

    Text(
        text = label,
        style = DsText.sub,
        color = if (selected) Palette.ink else Palette.textPrimary,
        modifier = Modifier
            .height(48.dp)
            .background(if (selected) Palette.accent else Palette.raised, CircleShape)
            .border(width = 2.dp, color = Palette.accent.copy(alpha = focusRingAlpha), shape = CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .focusable()
            .onFocusChanged { state -> isFocused = state.isFocused }
            .padding(horizontal = Space.md, vertical = Space.md),
    )
}

@Composable
private fun ClockInDoor(onClick: () -> Unit) {
    Door(
        label = stringResource(R.string.home_clock_in),
        hint = stringResource(R.string.a11y_clock_in_hint),
        fill = Palette.accent,
        textColor = Palette.ink,
        glow = Palette.accent,
        onClick = onClick,
    )
}

@Composable
private fun ClockOutDoor(onClick: () -> Unit) {
    Door(
        label = stringResource(R.string.home_clock_out),
        hint = stringResource(R.string.a11y_clock_out_hint),
        fill = Palette.clockedIn,
        textColor = Palette.ink,
        glow = Palette.clockedIn,
        onClick = onClick,
    )
}

/** The big round button. One static glow sits behind it. */
@Composable
private fun Door(label: String, hint: String, fill: Color, textColor: Color, glow: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "door-press")
    val focusRingAlpha by animateFloatAsState(if (isFocused) 1f else 0f, label = "door-focus-ring")

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
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .background(fill, CircleShape)
                .border(width = 3.dp, color = Palette.accent.copy(alpha = focusRingAlpha), shape = CircleShape)
                .clickable(interactionSource = interaction, indication = null, onClickLabel = hint, role = Role.Button, onClick = onClick)
                .focusable(interactionSource = interaction)
                .onFocusChanged { state -> isFocused = state.isFocused },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = label, style = DsText.titleSection, color = textColor, textAlign = TextAlign.Center, modifier = Modifier.padding(Space.md))
        }
    }
}

@Composable
private fun BreakButton(onBreak: Boolean, paidBreaks: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRingAlpha by animateFloatAsState(if (isFocused) 1f else 0f, label = "break-focus-ring")

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
                .border(width = 2.dp, color = Palette.accent.copy(alpha = focusRingAlpha), shape = RoundedCornerShape(28.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .focusable()
                .onFocusChanged { state -> isFocused = state.isFocused }
                .padding(top = 16.dp),
        )
        Text(
            text = if (onBreak) stringResource(R.string.home_break_on_break) else stringResource(if (paidBreaks) R.string.home_break_paid else R.string.home_break_unpaid),
            style = DsText.meta,
            color = Palette.textTertiary,
        )
    }
}
