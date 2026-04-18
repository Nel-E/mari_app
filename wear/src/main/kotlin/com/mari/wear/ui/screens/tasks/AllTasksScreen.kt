package com.mari.wear.ui.screens.tasks

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Text
import com.mari.shared.domain.Task

@Composable
fun AllTasksScreen(
    @Suppress("UNUSED_PARAMETER") navController: NavController,
    viewModel: AllTasksViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val selectedTask = uiState.selectedTask
    if (selectedTask != null) {
        TaskActionsContent(
            task = selectedTask,
            onExecute = { viewModel.onSetExecuting(selectedTask) },
            onComplete = { viewModel.onComplete(selectedTask) },
            onDelete = { viewModel.onDelete(selectedTask) },
            onDismiss = viewModel::onDismissActions,
        )
    } else {
        TaskListContent(
            tasks = uiState.tasks,
            onTaskClick = viewModel::onTaskClick,
        )
    }
}

@Composable
private fun TaskListContent(
    tasks: List<Task>,
    onTaskClick: (Task) -> Unit,
) {
    ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
        if (tasks.isEmpty()) {
            item { Text("No tasks") }
        }
        items(tasks) { task ->
            Chip(
                label = { Text(task.description, maxLines = 2) },
                secondaryLabel = { Text(task.status.name.lowercase().replace('_', ' ')) },
                onClick = { onTaskClick(task) },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
    }
}

@Composable
private fun TaskActionsContent(
    task: Task,
    onExecute: () -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
        item { Text(task.description, maxLines = 2) }
        item {
            Chip(
                label = { Text("Execute") },
                onClick = onExecute,
                colors = ChipDefaults.primaryChipColors(),
            )
        }
        item {
            Chip(
                label = { Text("Complete") },
                onClick = onComplete,
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
        item {
            Chip(
                label = { Text("Delete") },
                onClick = onDelete,
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
        item {
            CompactChip(
                label = { Text("Cancel") },
                onClick = onDismiss,
            )
        }
    }
}
