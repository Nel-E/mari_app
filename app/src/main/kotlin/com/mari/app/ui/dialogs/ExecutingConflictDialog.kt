package com.mari.app.ui.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ExecutingConflictDialog(
    executingDescription: String,
    onFinish: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Task in progress") },
        text = { Text("\"$executingDescription\" is currently executing. What would you like to do?") },
        confirmButton = {
            TextButton(onClick = onFinish) { Text("Finish") }
        },
        dismissButton = {
            TextButton(onClick = onPause) { Text("Pause") }
        },
        // Extra cancel action via the dismiss mechanism
        // A real implementation might use a custom layout for three buttons
    )
}
