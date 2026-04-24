package com.mari.app.ui.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.mari.app.ui.util.rememberCountdown

private const val CONFIRM_DELAY_MS = 2_000L

@Composable
fun DeleteConfirmDialog(
    taskDescription: String,
    onSoftDelete: () -> Unit,
    onPermanentDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val remaining by rememberCountdown(CONFIRM_DELAY_MS)
    val confirmEnabled = remaining == 0L
    val permanentDeleteLabel = if (confirmEnabled) "Delete forever" else "Delete forever (${(remaining / 1000) + 1}s)"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete task?") },
        text = { Text("\"$taskDescription\"\n\nArchive keeps the task hidden but recoverable. Delete forever removes it permanently and cannot be undone.") },
        confirmButton = {
            TextButton(onClick = onSoftDelete) {
                Text("Archive")
            }
            TextButton(onClick = onPermanentDelete, enabled = confirmEnabled) {
                Text(permanentDeleteLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
