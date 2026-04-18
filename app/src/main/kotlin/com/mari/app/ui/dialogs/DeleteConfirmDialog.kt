package com.mari.app.ui.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.mari.app.ui.util.rememberCountdown

private const val CONFIRM_DELAY_MS = 2_000L

@Composable
fun DeleteConfirmDialog(
    taskDescription: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val remaining by rememberCountdown(CONFIRM_DELAY_MS)
    val confirmEnabled = remaining == 0L
    val confirmLabel = if (confirmEnabled) "Delete" else "Delete (${(remaining / 1000) + 1}s)"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete task?") },
        text = { Text("\"$taskDescription\" will be soft-deleted.") },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
