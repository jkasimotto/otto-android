package com.otto.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

@Composable
fun SettingsHome(
    section: String,
    currentVersion: String,
    lockdownRemaining: String?,
    onStartLockdown: (Int) -> Unit,
    onDismiss: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onOpenLogs: () -> Unit,
    onSendFeedback: () -> Unit,
    onUpdate: () -> Unit
) {
    var pendingLockdownMinutes by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text(section) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Installed version: v$currentVersion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onOpenSystemSettings) { Text("Settings") }
                TextButton(onClick = onOpenUsageAccess) { Text("Usage access") }
                TextButton(onClick = onOpenLogs) { Text("Logs") }
                TextButton(onClick = onSendFeedback) { Text("Send feedback") }
                TextButton(onClick = onUpdate) { Text("Update") }

                LockdownControls(
                    lockdownRemaining = lockdownRemaining,
                    pendingMinutes = pendingLockdownMinutes,
                    onPick = { pendingLockdownMinutes = it },
                    onCancelPick = { pendingLockdownMinutes = null },
                    onConfirm = { minutes ->
                        pendingLockdownMinutes = null
                        onStartLockdown(minutes)
                    }
                )
            }
        }
    )
}

@Composable
private fun LockdownControls(
    lockdownRemaining: String?,
    pendingMinutes: Int?,
    onPick: (Int) -> Unit,
    onCancelPick: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    if (lockdownRemaining != null) {
        Text(
            text = "Locked down: $lockdownRemaining left",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        return
    }

    if (pendingMinutes != null) {
        Text(
            text = "Lock down for ${formatDuration(pendingMinutes)}? No early exit.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onConfirm(pendingMinutes) }) { Text("Lock down") }
            TextButton(onClick = onCancelPick) { Text("Cancel") }
        }
        return
    }

    Text(
        text = "Lockdown (only phone, messages, messenger, maps)",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { onPick(30) }) { Text("30m") }
        TextButton(onClick = { onPick(60) }) { Text("1h") }
        TextButton(onClick = { onPick(120) }) { Text("2h") }
        TextButton(onClick = { onPick(240) }) { Text("4h") }
    }
}

private fun formatDuration(minutes: Int): String {
    return if (minutes < 60) "${minutes}m" else "${minutes / 60}h"
}
