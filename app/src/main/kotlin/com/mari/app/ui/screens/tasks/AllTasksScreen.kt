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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mari.app.ui.components.EmptyState
import com.mari.app.ui.components.TaskRow
import com.mari.shared.domain.Task

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllTasksScreen(
    onNavigateUp: () -> Unit,
    onNavigateToAdd: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    viewModel: AllTasksViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { taskId -> onNavigateToEdit(taskId) }
    }

    var priorityPickTask by remember { mutableStateOf<Task?>(null) }
    var statusPickTask by remember { mutableStateOf<Task?>(null) }

    Scaffold(
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
                        TaskRow(
                            task = task,
                            onClick = { viewModel.onTaskClick(task) },
                            onPriorityClick = { priorityPickTask = it },
                            onStatusClick = { statusPickTask = it },
                        )
                    }
                }
            }
        }
    }

    priorityPickTask?.let { task ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ChangePrioritySheet(
            currentPriority = task.priority,
            sheetState = sheetState,
            onSelect = { newPriority ->
                viewModel.onQuickPriorityChange(task.id, newPriority)
                priorityPickTask = null
            },
            onDismiss = { priorityPickTask = null },
        )
    }

    statusPickTask?.let { task ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ChangeStatusSheet(
            currentStatus = task.status,
            sheetState = sheetState,
            onSelect = { newStatus ->
                viewModel.onQuickStatusChange(task.id, newStatus)
                statusPickTask = null
            },
            onDismiss = { statusPickTask = null },
        )
    }
}

