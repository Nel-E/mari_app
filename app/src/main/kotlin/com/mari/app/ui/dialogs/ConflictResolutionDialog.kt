package com.mari.app.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mari.shared.domain.Task

@Composable
fun ConflictResolutionDialog(
    local: Task,
    incoming: Task,
    onKeepPhone: () -> Unit,
    onKeepWatch: () -> Unit,
    onKeepBoth: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Sync conflict") },
        text = {
            Column {
                Text("Phone: ${local.name} (${local.status.name.lowercase()})")
                Text("Watch: ${incoming.name} (${incoming.status.name.lowercase()})")
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                )
                TextButton(onClick = onKeepBoth) {
                    Text("Keep both")
                }
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onKeepPhone) {
                Text("Keep phone")
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepWatch) {
                Text("Keep watch")
            }
        },
    )
}
