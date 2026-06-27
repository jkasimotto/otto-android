package com.otto.launcher.trace.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.otto.launcher.trace.domain.NextTraceActionKind
import com.otto.launcher.trace.domain.SleepEstimate
import com.otto.launcher.trace.domain.TraceCategory
import com.otto.launcher.trace.domain.TraceDashboardState
import com.otto.launcher.trace.domain.TraceTimelineItem
import com.otto.launcher.trace.domain.TraceType
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt

@Composable
fun TraceHomeLayer(
    state: TraceDashboardState,
    modifier: Modifier = Modifier,
    onOpenToday: () -> Unit,
    onOpenWeekly: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        TraceStrip(
            state = state,
            onOpenToday = onOpenToday,
            onOpenWeekly = onOpenWeekly
        )
        if (state.nextAction.kind == NextTraceActionKind.CONFIRM_SLEEP ||
            state.nextAction.kind == NextTraceActionKind.LOG_SLEEP
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = state.nextAction.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                state.nextAction.detail?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TraceStrip(
    state: TraceDashboardState,
    onOpenToday: () -> Unit,
    onOpenWeekly: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = traceStripText(state),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = onOpenToday)
        )
        TextButton(onClick = onOpenWeekly) {
            Text("Week")
        }
    }
}

@Composable
fun TraceCaptureSheet(
    state: TraceDashboardState,
    onDismiss: () -> Unit,
    onFoodCamera: () -> Unit,
    onDrinkCamera: () -> Unit,
    onImportFood: () -> Unit,
    onImportDrink: () -> Unit,
    onConfirmSleep: () -> Unit,
    onWeight: () -> Unit,
    onSleep: () -> Unit,
    onRecordMemo: () -> Unit,
    queuedMemoCount: Int,
    onToday: () -> Unit,
    onSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("Capture") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (state.nextAction.kind == NextTraceActionKind.CONFIRM_SLEEP) {
                    TraceActionButton(
                        icon = { Icon(Icons.Filled.Bedtime, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = "Save sleep estimate",
                        detail = state.nextAction.detail,
                        onClick = onConfirmSleep
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TraceSmallButton(
                        Icons.Filled.Mic,
                        if (queuedMemoCount > 0) "Record note ($queuedMemoCount)" else "Record note",
                        onRecordMemo
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TraceSmallButton(Icons.Filled.Restaurant, "Food", onFoodCamera)
                    TraceSmallButton(Icons.Filled.LocalDrink, "Drink", onDrinkCamera)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TraceSmallButton(Icons.Filled.MonitorWeight, "Weight", onWeight)
                    TraceSmallButton(Icons.Filled.Bedtime, "Sleep", onSleep)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TraceSmallButton(Icons.Filled.PhotoLibrary, "Import food", onImportFood)
                    TraceSmallButton(Icons.Filled.PhotoLibrary, "Import drink", onImportDrink)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TraceSmallButton(Icons.Filled.Today, "Today", onToday)
                    TraceSmallButton(Icons.Filled.Settings, "Settings", onSettings)
                }
                if (state.timeline.isNotEmpty()) {
                    Text(
                        text = "Today",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    state.timeline.takeLast(3).asReversed().forEach { item ->
                        TraceTimelineRow(
                            item = item,
                            controlsEnabled = false,
                            onHide = {},
                            onDrinkOnly = {},
                            onEditNote = {},
                            onEditWeight = {},
                            onEditSleep = {},
                            onDelete = {}
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun TraceWeightDialog(
    lastWeightKg: Double?,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var value by rememberSaveable(lastWeightKg) {
        mutableStateOf(lastWeightKg?.let { "%.1f".format(it) }.orEmpty())
    }
    val parsed = value.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = parsed != null && parsed in 20.0..300.0,
                onClick = {
                    parsed?.let(onSave)
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Weight") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.filter { char -> char.isDigit() || char == '.' }.take(5) },
                    label = { Text("kg") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        enabled = parsed != null,
                        onClick = { parsed?.let { value = "%.1f".format((it - 0.1).coerceAtLeast(0.0)) } }
                    ) {
                        Text("-0.1")
                    }
                    TextButton(
                        enabled = parsed != null,
                        onClick = { parsed?.let { value = "%.1f".format(it + 0.1) } }
                    ) {
                        Text("+0.1")
                    }
                }
            }
        }
    )
}

@Composable
fun TraceSleepDialog(
    estimate: SleepEstimate?,
    title: String = if (estimate != null) "Sleep estimate" else "Sleep",
    onDismiss: () -> Unit,
    onSave: (Instant, Instant, Boolean) -> Unit
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val now = remember { Instant.now() }
    val defaultEnd = estimate?.endAt ?: now
    val defaultStart = estimate?.startAt ?: defaultEnd.minusSeconds(7 * 60 * 60)
    var startText by rememberSaveable(estimate) { mutableStateOf(formatClock(defaultStart)) }
    var endText by rememberSaveable(estimate) { mutableStateOf(formatClock(defaultEnd)) }
    val parsed = remember(startText, endText) {
        parseSleepTimes(startText, endText, defaultEnd.atZone(zoneId).toLocalDate(), zoneId)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = parsed != null,
                onClick = {
                    parsed?.let { (start, end) ->
                        onSave(start, end, estimate == null || start != estimate.startAt || end != estimate.endAt)
                    }
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it.take(5) },
                    label = { Text("Start HH:mm") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it.take(5) },
                    label = { Text("End HH:mm") },
                    singleLine = true
                )
                parsed?.let { (start, end) ->
                    Text(
                        text = "${formatClock(start)}-${formatClock(end)} · ${formatDuration(Duration.between(start, end).toMinutes().toInt())}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}

@Composable
fun TraceTodayDialog(
    state: TraceDashboardState,
    onDismiss: () -> Unit,
    onHide: (String, Boolean) -> Unit,
    onDrinkOnly: (String, Boolean) -> Unit,
    onUpdateNote: (String, String?) -> Unit,
    onUpdateWeight: (String, Double) -> Unit,
    onUpdateSleep: (String, Instant, Instant, Boolean) -> Unit,
    onDelete: (String) -> Unit
) {
    var noteTarget by remember { mutableStateOf<TraceTimelineItem?>(null) }
    var weightTarget by remember { mutableStateOf<TraceTimelineItem?>(null) }
    var sleepTarget by remember { mutableStateOf<TraceTimelineItem?>(null) }
    var deleteTarget by remember { mutableStateOf<TraceTimelineItem?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("Today") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = traceStripText(state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.timeline.isEmpty()) {
                    Text("No traces yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.timeline, key = { it.traceId }) { item ->
                            TraceTimelineRow(
                                item = item,
                                onHide = { onHide(item.traceId, !item.hidden) },
                                onDrinkOnly = { onDrinkOnly(item.traceId, !item.isDrinkOnly) },
                                onEditNote = { noteTarget = item },
                                onEditWeight = { weightTarget = item },
                                onEditSleep = { sleepTarget = item },
                                onDelete = { deleteTarget = item }
                            )
                        }
                    }
                }
            }
        }
    )
    noteTarget?.let { target ->
        var text by rememberSaveable(target.traceId) { mutableStateOf(target.notes.orEmpty()) }
        AlertDialog(
            onDismissRequest = { noteTarget = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdateNote(target.traceId, text)
                        noteTarget = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { noteTarget = null }) { Text("Cancel") }
            },
            title = { Text("Note") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(240) },
                    minLines = 3,
                    maxLines = 5
                )
            }
        )
    }
    weightTarget?.let { target ->
        TraceWeightDialog(
            lastWeightKg = target.weightKg,
            onDismiss = { weightTarget = null },
            onSave = {
                onUpdateWeight(target.traceId, it)
                weightTarget = null
            }
        )
    }
    sleepTarget?.let { target ->
        target.endedAt?.let { endedAt ->
            TraceSleepDialog(
                estimate = SleepEstimate(target.occurredAt, endedAt),
                title = "Sleep",
                onDismiss = { sleepTarget = null },
                onSave = { startAt, endAt, adjusted ->
                    onUpdateSleep(target.traceId, startAt, endAt, adjusted)
                    sleepTarget = null
                }
            )
        }
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(target.traceId)
                        deleteTarget = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
            title = { Text("Delete ${target.title}?") },
            text = {
                Text(
                    text = "This removes the item from Trace.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
}

@Composable
fun TraceWeeklyDialog(
    state: TraceDashboardState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("This week") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TraceMetricLine("Food", "${state.weekly.foodPhotoCount} photos")
                TraceMetricLine("Food coverage", "${state.weekly.foodCoverageDays}/7 days")
                TraceMetricLine("Median eating window", state.weekly.medianEatingWindowMinutes?.let(::formatDuration) ?: "-")
                TraceMetricLine("Late food", "${state.weekly.lateFoodNights} nights")
                TraceMetricLine("Weight coverage", "${state.weekly.weightCoverageDays}/7 days")
                TraceMetricLine("Weight trend", state.weekly.weightTrendDeltaKg?.let { signedKg(it) } ?: "-")
                TraceMetricLine("Sleep coverage", "${state.weekly.sleepCoverageDays}/7 nights")
                TraceMetricLine("Median sleep", state.weekly.medianSleepDurationMinutes?.let(::formatDuration) ?: "-")
            }
        }
    )
}

@Composable
fun TraceSettingsDialog(
    state: TraceDashboardState,
    onDismiss: () -> Unit,
    onSetCategory: (TraceCategory, Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("Trace settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryToggle("Food photos", state.settings.foodEnabled) { onSetCategory(TraceCategory.FOOD, it) }
                CategoryToggle("Drink photos", state.settings.drinkEnabled) { onSetCategory(TraceCategory.DRINK, it) }
                CategoryToggle("Weight", state.settings.weightEnabled) { onSetCategory(TraceCategory.WEIGHT, it) }
                CategoryToggle("Sleep", state.settings.sleepEnabled) { onSetCategory(TraceCategory.SLEEP, it) }
            }
        }
    )
}

@Composable
private fun TraceActionButton(
    icon: @Composable () -> Unit,
    label: String,
    detail: String?,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                detail?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TraceSmallButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .height(44.dp)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun TraceTimelineRow(
    item: TraceTimelineItem,
    controlsEnabled: Boolean = true,
    onHide: () -> Unit,
    onDrinkOnly: () -> Unit,
    onEditNote: () -> Unit,
    onEditWeight: () -> Unit,
    onEditSleep: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item.media?.thumbnailPath?.let { path ->
            val bitmap = remember(path) {
                BitmapFactory.decodeFile(path)?.asImageBitmap()
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("${formatClock(item.occurredAt)}  ${item.title}", style = MaterialTheme.typography.bodyMedium)
            item.detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item.notes?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (controlsEnabled) {
                when {
                    item.type == TraceType.FOOD_PHOTO || item.type == TraceType.DRINK_PHOTO -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = onEditNote) { Text("Note") }
                            TextButton(onClick = onDrinkOnly) { Text(if (item.isDrinkOnly) "Food" else "Drink") }
                            IconButton(onClick = onHide) {
                                Icon(
                                    imageVector = if (item.hidden) Icons.Filled.Visibility else Icons.Filled.HideImage,
                                    contentDescription = null
                                )
                            }
                            TextButton(onClick = onDelete) { Text("Delete") }
                        }
                    }
                    item.type == TraceType.WEIGHT -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = onEditWeight) { Text("Edit") }
                            TextButton(onClick = onDelete) { Text("Delete") }
                        }
                    }
                    item.type == TraceType.SLEEP_SESSION -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = onEditSleep) { Text("Edit") }
                            TextButton(onClick = onDelete) { Text("Delete") }
                        }
                    }
                    else -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = onDelete) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TraceMetricLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}

@Composable
private fun CategoryToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

fun traceStripText(state: TraceDashboardState): String {
    val sleep = state.today.sleepDurationMinutes?.let { "Sleep ${formatDuration(it)}" } ?: "Sleep -"
    val weight = state.today.weightKg?.let { "${"%.1f".format(it)}kg" } ?: "Weight -"
    val food = "Food ${state.today.foodPhotoCount}"
    val window = state.today.eatingWindowMinutes?.let { "Window ${formatDuration(it)}" }
    return listOfNotNull(sleep, weight, food, window).joinToString(" · ")
}

fun formatClock(instant: Instant): String {
    val local = instant.atZone(ZoneId.systemDefault()).toLocalTime()
    return "%02d:%02d".format(local.hour, local.minute)
}

fun formatDuration(minutes: Int): String {
    val safeMinutes = minutes.coerceAtLeast(0)
    return "${safeMinutes / 60}h%02d".format(safeMinutes % 60)
}

private fun signedKg(value: Double): String {
    val rounded = (value * 10.0).roundToInt() / 10.0
    return "%+.1fkg".format(rounded)
}

private fun parseSleepTimes(
    startText: String,
    endText: String,
    endDate: LocalDate,
    zoneId: ZoneId
): Pair<Instant, Instant>? {
    return try {
        val startTime = LocalTime.parse(startText, DateTimeFormatter.ofPattern("HH:mm"))
        val endTime = LocalTime.parse(endText, DateTimeFormatter.ofPattern("HH:mm"))
        var startDate = endDate
        if (startTime > endTime) {
            startDate = endDate.minusDays(1)
        }
        val start = LocalDateTime.of(startDate, startTime).atZone(zoneId).toInstant()
        val end = LocalDateTime.of(endDate, endTime).atZone(zoneId).toInstant()
        if (end > start) start to end else null
    } catch (_: DateTimeParseException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun String.replaceFirstChar(transform: (Char) -> String): String {
    return if (isEmpty()) this else transform(this[0]) + substring(1)
}
