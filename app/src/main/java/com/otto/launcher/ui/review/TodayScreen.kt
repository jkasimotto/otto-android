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
import com.otto.launcher.ui.home.HomeUiState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TodayScreen(
    state: HomeUiState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Today") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("Sleep", state.ledger.sleepText)
                Metric("Weight", state.ledger.weightText)
                Metric("Food", state.ledger.foodText)
                Metric("Phone", state.ledger.phoneText)
                Metric("Inbox", "${state.inbox.count { it.kind.name == "NOTE" }} notes · ${state.inbox.count { it.kind.name == "TASK" }} tasks")
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

