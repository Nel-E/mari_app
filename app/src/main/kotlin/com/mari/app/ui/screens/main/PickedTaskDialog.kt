package com.mari.app.ui.screens.main

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun PickedTaskDialog(
    description: String,
    onStart: () -> Unit,
    onReroll: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Task Picked") },
        text = { Text(description) },
        confirmButton = {
            TextButton(onClick = onStart) { Text("Start") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReroll) { Text("Re-roll") }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        },
    )
}
