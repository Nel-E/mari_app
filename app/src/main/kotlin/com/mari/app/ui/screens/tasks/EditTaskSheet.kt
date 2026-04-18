package com.mari.app.ui.screens.tasks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mari.shared.domain.Task
import com.mari.shared.domain.TaskStatus
import com.mari.shared.domain.TaskValidation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTaskSheet(
    task: Task,
    sheetState: SheetState,
    onSave: (description: String, status: TaskStatus) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var description by remember(task.id) { mutableStateOf(task.description) }
    var descriptionError by remember(task.id) { mutableStateOf<String?>(null) }
    var showStatusSheet by remember { mutableStateOf(false) }
    var pendingStatus by remember(task.id) { mutableStateOf(task.status) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Text("Edit task", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = description,
                onValueChange = {
                    description = it
                    descriptionError = null
                },
                label = { Text("Description") },
                isError = descriptionError != null,
                supportingText = descriptionError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minLines = 2,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = pendingStatus.label(),
                onValueChange = {},
                readOnly = true,
                label = { Text("Status") },
                modifier = Modifier
                    .fillMaxWidth(),
                trailingIcon = {
                    TextButton(onClick = { showStatusSheet = true }) {
                        Text("Change")
                    }
                },
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    val result = TaskValidation.validateDescription(description)
                    if (result.isFailure) {
                        descriptionError = result.exceptionOrNull()?.message
                        return@Button
                    }
                    onSave(result.getOrThrow(), pendingStatus)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Delete")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showStatusSheet) {
        val innerSheetState = androidx.compose.material3.rememberModalBottomSheetState()
        ChangeStatusSheet(
            currentStatus = pendingStatus,
            sheetState = innerSheetState,
            onSelect = {
                pendingStatus = it
                showStatusSheet = false
            },
            onDismiss = { showStatusSheet = false },
        )
    }
}

private fun TaskStatus.label(): String = when (this) {
    TaskStatus.TO_BE_DONE -> "To Do"
    TaskStatus.PAUSED -> "Paused"
    TaskStatus.EXECUTING -> "Executing"
    TaskStatus.QUEUED -> "Queued"
    TaskStatus.COMPLETED -> "Completed"
    TaskStatus.DISCARDED -> "Discarded"
}
