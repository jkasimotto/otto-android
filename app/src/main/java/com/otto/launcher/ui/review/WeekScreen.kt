package com.otto.launcher.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.otto.launcher.trace.domain.TraceDashboardState

@Composable
fun WeekScreen(
    state: TraceDashboardState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Week") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("Sleep coverage", "${state.weekly.sleepCoverageDays} / 7 nights")
                Metric("Median sleep", state.weekly.medianSleepDurationMinutes?.let(::duration) ?: "not recorded")
                Metric("Late food nights", state.weekly.lateFoodNights.toString())
                Metric("Weight coverage", "${state.weekly.weightCoverageDays} / 7 days")
                Metric("Food photo days", "${state.weekly.foodCoverageDays} / 7")
                Metric("kJ reviewed days", "not set")
            }
        }
    )
}

@Composable
private fun Metric(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}

private fun duration(minutes: Int): String {
    return "${minutes / 60}h %02dm".format(minutes % 60)
}
