package com.otto.launcher.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.otto.launcher.domain.command.AppCommandResult
import com.otto.launcher.domain.command.CommandResult
import com.otto.launcher.domain.mode.OttoMode
import com.otto.launcher.domain.policy.AppGate
import com.otto.launcher.domain.time.DayPlanMode
import com.otto.launcher.domain.time.TimeCategoryIds
import com.otto.launcher.domain.time.TimeLedgerCalculator
import com.otto.launcher.domain.time.displayLabel
import com.otto.launcher.domain.trace.WeeklySleepDay
import com.otto.launcher.domain.usage.DailyPhoneUsage
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class FastCaptureAction {
    FOOD,
    WEIGHT,
    MOVE,
    NOTE
}

enum class LedgerAction {
    NOW,
    SLEEP,
    PEOPLE,
    MOVE,
    DRIFT
}

@Composable
fun HomeScreenV2(
    state: HomeUiState,
    commandResult: CommandResult,
    query: String,
    statusMessage: String?,
    onQueryChange: (String) -> Unit,
    onSubmitCommand: () -> Unit,
    onFastCapture: (FastCaptureAction) -> Unit,
    onFastCaptureLongPress: (FastCaptureAction) -> Unit,
    onLedgerAction: (LedgerAction) -> Unit,
    onTapSleepDay: (WeeklySleepDay) -> Unit,
    onAppResult: (AppCommandResult) -> Unit,
    onAppLongPress: (AppCommandResult) -> Unit,
    onOttoLongPress: () -> Unit,
    onWake: () -> Unit,
    onEmergency: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSearching = query.isNotBlank()
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        if (state.mode == OttoMode.SLEEP) {
            SleepModeHome(
                state = state,
                onWake = onWake,
                onEmergency = onEmergency,
                modifier = Modifier.align(Alignment.TopStart)
            )
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                HomeTopBar(
                    state = state,
                    onOttoLongPress = onOttoLongPress
                )
                if (!isSearching) {
                    val modeHeadline = when (state.mode) {
                        OttoMode.FOCUS -> "Focus mode"
                        OttoMode.WIND_DOWN -> "Wind-down"
                        else -> null
                    }
                    modeHeadline?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (state.timeLedger.dayPlanMode == DayPlanMode.LOW_SLEEP) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Minimum viable day",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Protect sleep tonight.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    SleepPanel(
                        weeklySleep = state.weeklySleep,
                        weeklyPhoneUsage = state.weeklyPhoneUsage,
                        weeklyNightUnlocks = state.weeklyNightUnlocks,
                        onLogSleep = { onLedgerAction(LedgerAction.SLEEP) },
                        onTapDay = onTapSleepDay
                    )
                }
            }

            AppSearchResults(
                result = commandResult,
                onAppResult = onAppResult,
                onAppLongPress = onAppLongPress,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxSize()
                    .padding(top = if (isSearching) 72.dp else 238.dp, bottom = 88.dp)
            )

            CommandBar(
                query = query,
                onQueryChange = onQueryChange,
                onSubmit = onSubmitCommand,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            )
        }

        statusMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(bottom = 58.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeTopBar(
    state: HomeUiState,
    onOttoLongPress: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column {
            Text(
                text = "Otto",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onLongClick = onOttoLongPress
                )
            )
            Text(
                text = "${state.timeLedger.date.format(DateTimeFormatter.ofPattern("EEE d MMM"))} · ${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ModeChip(state = state)
    }
}

@Composable
fun ModeChip(state: HomeUiState) {
    Text(
        text = state.timeLedger.activeBlock?.category?.displayLabel()
            ?: when (state.mode) {
                OttoMode.OPEN -> "Open"
                OttoMode.FOCUS -> "Focus"
                OttoMode.WIND_DOWN -> "Wind-down"
                OttoMode.SLEEP -> "Sleep"
            },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun TodayLedger(
    state: HomeUiState,
    onLedgerAction: (LedgerAction) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        LedgerRow("Now", nowText(state)) { onLedgerAction(LedgerAction.NOW) }
        LedgerRow("Sleep", rowValue(state, TimeCategoryIds.SLEEP, state.ledger.sleepText)) {
            onLedgerAction(LedgerAction.SLEEP)
        }
        LedgerRow("People", rowValue(state, TimeCategoryIds.RELATIONSHIPS, "not recorded")) {
            onLedgerAction(LedgerAction.PEOPLE)
        }
        LedgerRow("Move", rowValue(state, TimeCategoryIds.MOVEMENT, "not recorded")) {
            onLedgerAction(LedgerAction.MOVE)
        }
        LedgerRow("Drift", rowValue(state, TimeCategoryIds.DIGITAL_DRIFT, if (state.timeLedger.hasUsageAccess) "0m" else "usage access off")) {
            onLedgerAction(LedgerAction.DRIFT)
        }
    }
}

private fun nowText(state: HomeUiState): String {
    val active = state.timeLedger.activeBlock ?: return "no active block"
    return "${active.category.name} · ${TimeLedgerCalculator.formatDuration(active.elapsedMinutes)}"
}

private fun rowValue(state: HomeUiState, categoryId: String, fallback: String): String {
    return state.timeLedger.rows.firstOrNull { it.categoryId == categoryId }?.value ?: fallback
}

@Composable
private fun LedgerRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(78.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FastCaptureRow(
    onAction: (FastCaptureAction) -> Unit,
    onLongPress: (FastCaptureAction) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        FastCaptureLabel("Food", FastCaptureAction.FOOD, onAction, onLongPress)
        FastCaptureLabel("Weight", FastCaptureAction.WEIGHT, onAction, onLongPress)
        FastCaptureLabel("Move", FastCaptureAction.MOVE, onAction, onLongPress)
        FastCaptureLabel("Note", FastCaptureAction.NOTE, onAction, onLongPress)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FastCaptureLabel(
    label: String,
    action: FastCaptureAction,
    onAction: (FastCaptureAction) -> Unit,
    onLongPress: (FastCaptureAction) -> Unit
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { onAction(action) },
            onLongClick = { onLongPress(action) }
        )
    )
}

@Composable
fun CommandBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.padding(vertical = 12.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge.copy(
            color = MaterialTheme.colorScheme.onBackground,
            letterSpacing = 0.sp
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (query.isBlank()) {
                    Text(
                        text = "Type app or command...",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
fun SleepPanel(
    weeklySleep: List<WeeklySleepDay>,
    weeklyPhoneUsage: List<DailyPhoneUsage>,
    weeklyNightUnlocks: Map<LocalDate, List<Instant>>,
    onLogSleep: () -> Unit,
    onTapDay: (WeeklySleepDay) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sleep",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Log",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onLogSleep)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            weeklySleep.forEach { day ->
                SleepDayRow(
                    day = day,
                    marks = weeklyNightUnlocks[day.date].orEmpty(),
                    onTap = { onTapDay(day) }
                )
            }
            SleepTimeAxis()
        }
        PhoneUsageWeekChart(usage = weeklyPhoneUsage, targetMinutes = 60)
    }
}

@Composable
private fun PhoneUsageWeekChart(usage: List<DailyPhoneUsage>, targetMinutes: Int) {
    val barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    val overColor = MaterialTheme.colorScheme.onSurface
    val targetColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Phone",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "target <1h",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (usage.isEmpty()) {
            // No early return here: returning out of a @Composable content lambda corrupts the slot
            // table and crashes the launcher on open. Use if/else so the groups always balance.
            Text(
                text = "usage access off",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // Scale so the target line and the worst day both stay on-chart.
            val maxMinutes = (usage.maxOf { it.totalMinutes }).coerceAtLeast(targetMinutes * 2)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                val w = size.width
                val h = size.height
                val slotWidth = w / usage.size
                val barWidth = slotWidth * 0.5f

                usage.forEachIndexed { index, day ->
                    val barHeight = (day.totalMinutes.toFloat() / maxMinutes) * h
                    val left = index * slotWidth + (slotWidth - barWidth) / 2f
                    drawRect(
                        color = if (day.totalMinutes > targetMinutes) overColor else barColor,
                        topLeft = androidx.compose.ui.geometry.Offset(left, h - barHeight),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                    )
                }

                val targetY = h - (targetMinutes.toFloat() / maxMinutes) * h
                drawLine(
                    color = targetColor,
                    start = androidx.compose.ui.geometry.Offset(0f, targetY),
                    end = androidx.compose.ui.geometry.Offset(w, targetY),
                    strokeWidth = 2.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        floatArrayOf(0f, 6.dp.toPx())
                    )
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                usage.forEach { day ->
                    Text(
                        text = day.date.format(DateTimeFormatter.ofPattern("EEEEE")),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                        color = labelColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SleepDayRow(day: WeeklySleepDay, marks: List<Instant>, onTap: () -> Unit) {
    val isToday = day.date == LocalDate.now()
    val dayLabel = if (isToday) "Today" else day.date.format(DateTimeFormatter.ofPattern("EEE"))
    val barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    val emptyColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val targetColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    val markColor = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
    // Red as a warning: if the "now" line is visible inside the sleep window, the phone is in use
    // when it should not be.
    val nowColor = MaterialTheme.colorScheme.error

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = dayLabel,
            style = MaterialTheme.typography.bodySmall,
            color = if (isToday) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(42.dp)
        )

        // Chart bar spanning 20:00–12:00 (16h window across midnight)
        val windowStartMinutes = 20 * 60  // 20:00
        val windowMinutes = 16 * 60       // 16 hours

        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
        ) {
            val w = size.width
            val h = size.height

            fun windowFraction(minuteOfDay: Int): Float {
                val fromStart = if (minuteOfDay >= windowStartMinutes) minuteOfDay - windowStartMinutes
                                else minuteOfDay + 24 * 60 - windowStartMinutes
                return fromStart.toFloat() / windowMinutes
            }

            drawRect(color = emptyColor, size = size)
            if (day.startAt != null && day.endAt != null) {
                val zone = ZoneId.systemDefault()
                val startLocal = day.startAt.atZone(zone).toLocalTime()
                val endLocal = day.endAt.atZone(zone).toLocalTime()
                val s = windowFraction(startLocal.hour * 60 + startLocal.minute).coerceIn(0f, 1f)
                val e = windowFraction(endLocal.hour * 60 + endLocal.minute).coerceIn(0f, 1f)
                if (e > s) {
                    drawRect(
                        color = barColor,
                        topLeft = androidx.compose.ui.geometry.Offset(s * w, 0f),
                        size = androidx.compose.ui.geometry.Size((e - s) * w, h)
                    )
                }
            }

            val midnightX = (4f / 16f) * w
            drawLine(
                color = emptyColor.copy(alpha = 0.8f),
                start = androidx.compose.ui.geometry.Offset(midnightX, 0f),
                end = androidx.compose.ui.geometry.Offset(midnightX, h),
                strokeWidth = 1.dp.toPx()
            )

            // Target sleep window guides at 21:00 and 06:00 — "what good looks like"
            val targetDash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                floatArrayOf(2.dp.toPx(), 2.dp.toPx())
            )
            listOf(21 * 60, 6 * 60).forEach { minuteOfDay ->
                val x = windowFraction(minuteOfDay) * w
                drawLine(
                    color = targetColor,
                    start = androidx.compose.ui.geometry.Offset(x, 0f),
                    end = androidx.compose.ui.geometry.Offset(x, h),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = targetDash
                )
            }

            // Each phone unlock through the night, drawn on top so disruptions over the sleep bar show.
            val markZone = ZoneId.systemDefault()
            marks.forEach { inst ->
                val local = inst.atZone(markZone).toLocalTime()
                val frac = windowFraction(local.hour * 60 + local.minute)
                if (frac in 0f..1f) {
                    val x = frac * w
                    drawLine(
                        color = markColor,
                        start = androidx.compose.ui.geometry.Offset(x, 0f),
                        end = androidx.compose.ui.geometry.Offset(x, h),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
            }

            // Current-time line on whichever night row contains "now" (yellow). Nothing midday,
            // when the clock sits outside the 20:00-12:00 window.
            val nowDate = LocalDate.now()
            val nowTime = LocalTime.now()
            val nowNightDate = when {
                nowTime >= LocalTime.of(20, 0) -> nowDate
                nowTime < LocalTime.of(12, 0) -> nowDate.minusDays(1)
                else -> null
            }
            if (nowNightDate == day.date) {
                val nowX = windowFraction(nowTime.hour * 60 + nowTime.minute) * w
                drawLine(
                    color = nowColor,
                    start = androidx.compose.ui.geometry.Offset(nowX, 0f),
                    end = androidx.compose.ui.geometry.Offset(nowX, h),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
        }

        val durationText = if (day.startAt != null && day.endAt != null) {
            val mins = Duration.between(day.startAt, day.endAt).toMinutes().toInt()
            val h = mins / 60
            val m = mins % 60
            if (h > 0) "${h}h ${m.toString().padStart(2, '0')}m" else "${m}m"
        } else {
            "—"
        }
        Text(
            text = durationText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp)
        )
    }
}

@Composable
private fun SleepTimeAxis() {
    // 7 labels span 12h (8p-8a) out of the 16h window; final 4h (8a-12p) is a trailing spacer.
    // SpaceBetween over weight(12) + Spacer weight(4) places each label at the correct fraction.
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val labelStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Spacer(Modifier.width(42.dp))
        Row(modifier = Modifier.weight(12f), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("8p", "10p", "12a", "2a", "4a", "6a", "8a").forEach { label ->
                Text(text = label, style = labelStyle, color = labelColor)
            }
        }
        Spacer(modifier = Modifier.weight(4f))
        Spacer(Modifier.width(56.dp))
    }
}

@Composable
fun AppSearchResults(
    result: CommandResult,
    onAppResult: (AppCommandResult) -> Unit,
    onAppLongPress: (AppCommandResult) -> Unit,
    modifier: Modifier = Modifier
) {
    if (result !is CommandResult.AppResults || result.results.isEmpty()) return
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(result.results, key = { it.packageName + it.activityName }) { app ->
            AppSearchRow(app, onAppResult, onAppLongPress)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppSearchRow(
    app: AppCommandResult,
    onAppResult: (AppCommandResult) -> Unit,
    onAppLongPress: (AppCommandResult) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onAppResult(app) },
                onLongClick = { onAppLongPress(app) }
            )
            .padding(vertical = 8.dp)
    ) {
        Text(app.label, style = MaterialTheme.typography.titleMedium)
        Text(
            text = appSubtitle(app),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun appSubtitle(app: AppCommandResult): String {
    return when (val gate = app.gate) {
        AppGate.Allowed -> app.packageName
        AppGate.AdminHidden -> "hidden maintenance"
        AppGate.Blocked -> "blocked by Shield"
        is AppGate.Distraction -> "blocked by Shield"
        is AppGate.WorkWindowClosed -> "available ${gate.label}"
    }
}

@Composable
private fun SleepModeHome(
    state: HomeUiState,
    onWake: () -> Unit,
    onEmergency: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Text("Sleep mode", style = MaterialTheme.typography.titleLarge)
        state.ledger.activeSleepStartAt?.let {
            Text(
                text = "Started ${state.ledger.sleepText.removePrefix("active since ")}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(
                text = "Wake",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.clickable(onClick = onWake)
            )
            Text(
                text = "Emergency",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onEmergency)
            )
        }
    }
}
