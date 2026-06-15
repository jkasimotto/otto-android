package com.otto.launcher.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.otto.launcher.domain.command.AppCommandResult

@Composable
fun DistractionGateDialog(
    app: AppCommandResult,
    challengeCode: String,
    driftText: String?,
    isOverDriftCap: Boolean,
    onDismiss: () -> Unit,
    onOpen: (reason: String, timeboxMinutes: Int) -> Unit
) {
    var reason by rememberSaveable(app.packageName) { mutableStateOf("") }
    var replacing by rememberSaveable(app.packageName) { mutableStateOf("") }
    var challenge by rememberSaveable(app.packageName, challengeCode) { mutableStateOf("") }
    var selectedMinutes by rememberSaveable(app.packageName) { mutableStateOf(10) }
    var error by rememberSaveable(app.packageName) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        title = { Text("Open ${app.label}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                driftText?.let {
                    Text(
                        text = "Digital drift today: $it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = if (isOverDriftCap) {
                        "Digital drift cap reached."
                    } else {
                        "This will spend from Digital drift."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = {
                        reason = it.take(160)
                        error = null
                    },
                    label = { Text("Reason") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )
                if (isOverDriftCap) {
                    OutlinedTextField(
                        value = replacing,
                        onValueChange = {
                            replacing = it.take(80)
                            error = null
                        },
                        label = { Text("What is this replacing?") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                Text("Challenge", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = "Type: $challengeCode",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = challenge,
                    onValueChange = {
                        challenge = it.filter(Char::isLetter).uppercase().take(challengeCode.length)
                        error = null
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeboxButton(5, selectedMinutes) { selectedMinutes = 5 }
                    TimeboxButton(10, selectedMinutes) { selectedMinutes = 10 }
                    TimeboxButton(15, selectedMinutes) { selectedMinutes = 15 }
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    TextButton(
                        onClick = {
                            val cleanReason = reason.trim()
                            when {
                                cleanReason.length < 10 -> error = "Reason needs 10 characters."
                                isOverDriftCap && replacing.trim().length < 3 -> error = "Name what this replaces."
                                challenge != challengeCode -> error = "Challenge does not match."
                                else -> onOpen(cleanReason, selectedMinutes)
                            }
                        }
                    ) {
                        Text("Open for $selectedMinutes min")
                    }
                }
            }
        }
    )
}

@Composable
private fun TimeboxButton(
    minutes: Int,
    selected: Int,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Text(
            text = "$minutes min",
            color = if (minutes == selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}
