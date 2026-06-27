package com.otto.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun SettingsHome(
    section: String,
    onDismiss: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onOpenTimeBudget: () -> Unit,
    onOpenTimeReview: () -> Unit,
    onOpenWeeklyTimeReview: () -> Unit,
    onOpenLogs: () -> Unit,
    onSendFeedback: () -> Unit,
    onUpdate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text(section) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpenSystemSettings) { Text("Settings") }
                TextButton(onClick = onOpenTimeBudget) { Text("Time Ledger") }
                TextButton(onClick = onOpenTimeReview) { Text("Review today") }
                TextButton(onClick = onOpenWeeklyTimeReview) { Text("Review week") }
                TextButton(onClick = onOpenUsageAccess) { Text("Usage access") }
                TextButton(onClick = onOpenLogs) { Text("Logs") }
                TextButton(onClick = onSendFeedback) { Text("Send feedback") }
                TextButton(onClick = onUpdate) { Text("Update") }
            }
        }
    )
}
