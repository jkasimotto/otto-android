package com.otto.launcher.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
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
import java.time.LocalDate
import java.time.LocalTime
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
                    Text(
                        text = when (state.mode) {
                            OttoMode.FOCUS -> "Focus mode"
                            OttoMode.WIND_DOWN -> "Wind-down"
                            else -> "What are you here to do?"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
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
                    TodayLedger(
                        state = state,
                        onLedgerAction = onLedgerAction
                    )
                    FastCaptureRow(
                        onAction = onFastCapture,
                        onLongPress = onFastCaptureLongPress
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
