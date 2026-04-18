package com.mari.app.ui.screens.main

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mari.app.ui.dialogs.ConflictResolutionDialog
import com.mari.app.ui.dialogs.ExecutingConflictDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToTasks: () -> Unit,
    onNavigateToAdd: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mari") },
                actions = {
                    AllTasksIconButton(onClick = onNavigateToTasks)
                    SettingsIconButton(onClick = onNavigateToSettings)
                },
            )
        },
        floatingActionButton = {
            AddTaskFab(onClick = onNavigateToAdd)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val cta = uiState.ctaState) {
                is MainCtaState.AddTaskOnly -> {
                    Text(
                        text = "No tasks yet",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onNavigateToAdd) {
                        Text("Add your first task")
                    }
                }

                is MainCtaState.MarkExecutingComplete -> {
                    Text(
                        text = "Executing",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = cta.task.description,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = viewModel::completeExecutingTask) {
                        Text("Mark Complete")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = viewModel::openExecutingSheet) {
                        Text("Manage Task")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onNavigateToTasks) {
                        Text("View All Tasks")
                    }
                }

                is MainCtaState.ShakeToPick -> {
                    Text(
                        text = "Shake to Pick",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Shake your phone to randomly select a task",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedButton(onClick = onNavigateToTasks) {
                        Text("View All Tasks")
                    }
                }
            }
        }
    }

    if (uiState.showExecutingSheet) {
        val executingTask = (uiState.ctaState as? MainCtaState.MarkExecutingComplete)?.task
        if (executingTask != null) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ExecutingBottomSheet(
                taskDescription = executingTask.description,
                sheetState = sheetState,
                onComplete = viewModel::completeExecutingTask,
                onPause = viewModel::pauseExecutingTask,
                onReset = viewModel::resetExecutingTask,
                onDismiss = viewModel::closeExecutingSheet,
            )
        }
    }

    uiState.pickedTask?.let { task ->
        PickedTaskDialog(
            description = task.description,
            onStart = viewModel::onStartPicked,
            onReroll = viewModel::onRerollPicked,
            onCancel = viewModel::onDismissPicked,
        )
    }

    uiState.shakeConflict?.let { conflict ->
        ExecutingConflictDialog(
            executingDescription = conflict.existing.description,
            onFinish = viewModel::onShakeConflictFinish,
            onPause = viewModel::onShakeConflictPause,
            onCancel = viewModel::onDismissShakeConflict,
        )
    }

    uiState.pendingSyncConflict?.let { conflict ->
        ConflictResolutionDialog(
            local = conflict.local,
            incoming = conflict.incoming,
            onKeepPhone = viewModel::keepPhoneConflict,
            onKeepWatch = viewModel::keepWatchConflict,
            onKeepBoth = viewModel::keepBothConflict,
            onCancel = viewModel::cancelSyncConflict,
        )
    }
}
