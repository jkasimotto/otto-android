package com.otto.launcher.ui.capture

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun SleepStartDialog(
    onDismiss: () -> Unit,
    onStartSleep: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onStartSleep()
                    onDismiss()
                }
            ) {
                Text("Start sleep")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Start sleep mode?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "This records sleep start and hides non-essential apps until wake.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    )
}

