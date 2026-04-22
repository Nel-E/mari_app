package com.mari.wear.ui.screens.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Text
import com.mari.shared.domain.Task
import com.mari.wear.ui.nav.WearRoute

@Composable
fun MainScreen(
    navController: NavController,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        uiState.conflict != null -> ConflictContent(
            conflict = uiState.conflict!!,
            onFinish = viewModel::onConflictFinish,
            onPause = viewModel::onConflictPause,
            onDismiss = viewModel::onDismissConflict,
        )
        uiState.pickedTask != null -> PickedTaskContent(
            task = uiState.pickedTask!!,
            onStart = viewModel::onStartPicked,
            onDismiss = viewModel::onDismissPicked,
        )
        else -> CtaContent(
            ctaState = uiState.ctaState,
            onComplete = viewModel::completeExecutingTask,
            onPause = viewModel::pauseExecutingTask,
            onNavigateAdd = { navController.navigate(WearRoute.ADD) },
            onNavigateTasks = { navController.navigate(WearRoute.TASKS) },
            onNavigateSettings = { navController.navigate(WearRoute.SETTINGS) },
        )
    }
}

@Composable
private fun CtaContent(
    ctaState: WearCtaState,
    onComplete: () -> Unit,
    onPause: () -> Unit,
    onNavigateAdd: () -> Unit,
    onNavigateTasks: () -> Unit,
    onNavigateSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (ctaState) {
            is WearCtaState.MarkExecutingComplete -> {
                Text(
                    text = ctaState.task.name,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Spacer(Modifier.height(8.dp))
                Chip(
                    label = { Text("Complete") },
                    onClick = onComplete,
                    colors = ChipDefaults.primaryChipColors(),
                )
                Spacer(Modifier.height(4.dp))
                CompactChip(
                    label = { Text("Pause") },
                    onClick = onPause,
                )
            }
            WearCtaState.ShakeToPick -> {
                Text("Shake to pick a task", textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                CompactChip(
                    label = { Text("View Tasks") },
                    onClick = onNavigateTasks,
                )
                Spacer(Modifier.height(4.dp))
                CompactChip(
                    label = { Text("Add Task") },
                    onClick = onNavigateAdd,
                )
            }
            WearCtaState.AddTaskOnly -> {
                Chip(
                    label = { Text("Add Task") },
                    onClick = onNavigateAdd,
                    colors = ChipDefaults.primaryChipColors(),
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CompactChip(
                        label = { Text("Tasks") },
                        onClick = onNavigateTasks,
                    )
                    Spacer(Modifier.width(4.dp))
                    CompactChip(
                        label = { Text("Settings") },
                        onClick = onNavigateSettings,
                    )
                }
            }
        }
    }
}

@Composable
private fun PickedTaskContent(
    task: Task,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Picked:", textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            text = task.name,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(8.dp))
        Chip(
            label = { Text("Start") },
            onClick = onStart,
            colors = ChipDefaults.primaryChipColors(),
        )
        Spacer(Modifier.height(4.dp))
        CompactChip(
            label = { Text("Dismiss") },
            onClick = onDismiss,
        )
    }
}

@Composable
private fun ConflictContent(
    conflict: WearPickedConflict,
    onFinish: () -> Unit,
    onPause: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Executing:", textAlign = TextAlign.Center)
        Text(
            text = conflict.existing.name,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text("New pick:", textAlign = TextAlign.Center)
        Text(
            text = conflict.incoming.name,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Spacer(Modifier.height(8.dp))
        Chip(
            label = { Text("Finish & Start") },
            onClick = onFinish,
            colors = ChipDefaults.primaryChipColors(),
        )
        Spacer(Modifier.height(4.dp))
        CompactChip(
            label = { Text("Pause & Start") },
            onClick = onPause,
        )
        Spacer(Modifier.height(4.dp))
        CompactChip(
            label = { Text("Dismiss") },
            onClick = onDismiss,
        )
    }
}
