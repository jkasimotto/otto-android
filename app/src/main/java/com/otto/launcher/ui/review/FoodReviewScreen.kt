package com.otto.launcher.ui.review

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.otto.launcher.trace.data.FoodEntryEntity
import java.time.ZoneId

@Composable
fun FoodReviewScreen(
    entries: List<FoodEntryEntity>,
    onDismiss: () -> Unit,
    onSetEnergy: (String, Int?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        title = { Text("Food review") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "${entries.size} unresolved",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (entries.isEmpty()) {
                    Text("No unresolved food.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 430.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(entries, key = { it.id }) { entry ->
                            FoodReviewRow(entry, onSetEnergy)
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun FoodReviewRow(
    entry: FoodEntryEntity,
    onSetEnergy: (String, Int?) -> Unit
) {
    var value by rememberSaveable(entry.id) { mutableStateOf(entry.energyKj?.toString().orEmpty()) }
    val bitmap = remember(entry.thumbnailUri) {
        entry.thumbnailUri?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = formatClock(entry),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = { value = it.filter(Char::isDigit).take(6) },
            label = { Text("kJ") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        TextButton(
            enabled = value.toIntOrNull() != null,
            onClick = { onSetEnergy(entry.id, value.toIntOrNull()) }
        ) {
            Text("Save")
        }
        TextButton(onClick = { onSetEnergy(entry.id, null) }) {
            Text("Skip")
        }
    }
}

private fun formatClock(entry: FoodEntryEntity): String {
    val time = entry.capturedAt.atZone(ZoneId.systemDefault()).toLocalTime()
    return "%02d:%02d".format(time.hour, time.minute)
}

