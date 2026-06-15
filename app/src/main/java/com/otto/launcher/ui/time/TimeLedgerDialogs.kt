package com.otto.launcher.ui.time

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.otto.launcher.domain.time.DailyTimeReview
import com.otto.launcher.domain.time.TimeCategoryIds
import com.otto.launcher.domain.time.TimeLedgerCalculator
import com.otto.launcher.domain.time.TimeMode
import com.otto.launcher.domain.time.TodayTimeLedger
import com.otto.launcher.domain.time.WeeklyTimeLedger
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun StartBlockDialog(
    onDismiss: () -> Unit,
    onStartMode: (TimeMode) -> Unit,
    onStartCategory: (categoryId: String, label: String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Start block") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BlockChoice("Focus work") { onStartMode(TimeMode.FOCUS_WORK) }
                BlockChoice("Social") { onStartMode(TimeMode.RELATIONSHIP) }
                BlockChoice("Movement") { onStartMode(TimeMode.MOVEMENT) }
                BlockChoice("Rest") { onStartMode(TimeMode.REST) }
                BlockChoice("Admin") { onStartCategory(TimeCategoryIds.ADMIN, "admin") }
                BlockChoice("Commute") { onStartCategory(TimeCategoryIds.COMMUTE, "commute") }
                BlockChoice("Other") { onStartCategory(TimeCategoryIds.UNKNOWN, "other") }
            }
        }
    )
}

@Composable
fun TodayTimeReviewScreen(
    review: DailyTimeReview,
    onDismiss: () -> Unit,
    onPulse: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Today") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                review.rows.forEach { row ->
                    Metric(row.label, row.value)
                }
                Metric(
                    "Unknown",
                    if (review.unknownGaps.isEmpty()) {
                        "none"
                    } else {
                        TimeLedgerCalculator.formatDuration(review.unknownGaps.sumOf { it.minutes })
                    }
                )
                Text("Did today feel time-rich?", style = MaterialTheme.typography.bodyMedium)
                PulseRow(
                    selected = review.timeAffluence,
                    onPulse = onPulse
                )
                if (review.unknownGaps.isNotEmpty()) {
                    Text("Review unknown", style = MaterialTheme.typography.bodyMedium)
                    review.unknownGaps.take(4).forEach { gap ->
                        Metric(
                            "${formatClock(gap.startAt)}-${formatClock(gap.endAt)}",
                            TimeLedgerCalculator.formatDuration(gap.minutes)
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun WeeklyTimeReviewScreen(
    ledger: WeeklyTimeLedger,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Week") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ledger.rows.forEach { row ->
                    Metric(row.label, row.value)
                }
                ledger.experimentSuggestion?.let {
                    Text("One experiment", style = MaterialTheme.typography.bodyMedium)
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    )
}

@Composable
fun TimeBudgetScreen(
    today: TodayTimeLedger,
    week: WeeklyTimeLedger,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Time Ledger") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Today", style = MaterialTheme.typography.bodyMedium)
                today.rows.forEach { row ->
                    if (row.budgetMinutes != null) Metric(row.label, row.value)
                }
                Text("Week", style = MaterialTheme.typography.bodyMedium)
                week.rows.forEach { row ->
                    if (row.budgetMinutes != null) Metric(row.label, row.value)
                }
                Text(
                    "Usage access: ${if (today.hasUsageAccess) "on" else "off"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun BlockChoice(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}

@Composable
private fun Metric(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}

@Composable
private fun PulseRow(selected: Int?, onPulse: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0, 2, 4, 6, 8, 10).forEach { value ->
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected == value) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.clickable { onPulse(value) }
            )
        }
    }
}

private fun formatClock(instant: java.time.Instant): String {
    val local = instant.atZone(ZoneId.systemDefault()).toLocalTime()
    return local.format(DateTimeFormatter.ofPattern("HH:mm"))
}
