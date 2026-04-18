package com.mari.app.ui.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun MarkCompleteDialog(
    taskDescription: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mark complete?") },
        text = { Text("Mark \"$taskDescription\" as completed?") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Complete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
