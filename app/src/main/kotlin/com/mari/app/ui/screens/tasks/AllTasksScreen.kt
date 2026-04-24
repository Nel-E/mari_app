package com.mari.app.ui.screens.tasks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mari.app.ui.components.EmptyState
import com.mari.app.ui.components.TaskRow
import com.mari.app.ui.dialogs.ExecutingConflictDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllTasksScreen(
    onNavigateUp: () -> Unit,
    onNavigateToAdd: () -> Unit,
    viewModel: AllTasksViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val reminderTemplates by viewModel.reminderTemplates.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val editError = uiState.editError
    LaunchedEffect(editError) {
        if (editError != null) {
            snackbarHostState.showSnackbar(editError)
            viewModel.onClearEditError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("All Tasks") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    SortMenu(currentMode = uiState.filterState.sortMode, onSelect = viewModel::onSortModeChange)
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToAdd) {
                Icon(Icons.Default.Add, contentDescription = "Add Task")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TaskSearchBar(query = uiState.filterState.query, onQueryChange = viewModel::onQueryChange)
            FilterChipsRow(
                selectedStatuses = uiState.filterState.selectedStatuses,
                onToggle = viewModel::onStatusToggle,
            )
            if (uiState.tasks.isEmpty()) {
                EmptyState(title = "No tasks", subtitle = "Add a task or adjust your filters")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(uiState.tasks, key = { it.id }) { task ->
                        TaskRow(task = task, onClick = { viewModel.onTaskClick(task) })
                    }
                }
            }
        }
    }

    uiState.selectedTask?.let { task ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        EditTaskSheet(
            task = task,
            sheetState = sheetState,
            reminderTemplates = reminderTemplates,
            editError = uiState.editError,
            onSave = { name, desc, status, dueAt, dueKind, reminders, colorHex ->
                viewModel.onSaveEdit(task.id, name, desc, status, dueAt, dueKind, reminders, colorHex)
            },
            onDelete = { viewModel.onPermanentDeleteTask(task) },
            onDismiss = viewModel::onDismissEdit,
        )
    }

    uiState.executingConflict?.let { conflict ->
        ExecutingConflictDialog(
            executingDescription = conflict.existing.name,
            onFinish = viewModel::onConflictFinish,
            onPause = viewModel::onConflictPause,
            onCancel = viewModel::onDismissConflict,
        )
    }
}
