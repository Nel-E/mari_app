package com.mari.app.ui.screens.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mari.app.domain.model.AppUpdateReleaseNote

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateAvailableScreen(
    onNavigateUp: () -> Unit,
    viewModel: UpdateAvailableViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val update = uiState.update

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Update Available") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            if (update == null) {
                Spacer(modifier = Modifier.height(32.dp))
                Text("No update available.", style = MaterialTheme.typography.bodyLarge)
                return@Column
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = update.versionName, style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "${update.track.uppercase()} · ${update.fileSizeBytes / 1024 / 1024} MB",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (val state = uiState.downloadState) {
                DownloadState.Idle -> {
                    Button(onClick = viewModel::download, modifier = Modifier.fillMaxWidth()) {
                        Text("Download & Install")
                    }
                }
                is DownloadState.Downloading -> {
                    Text("Downloading… ${(state.progress * 100).toInt()}%")
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                }
                is DownloadState.Ready -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.install(state.file) }, modifier = Modifier.weight(1f)) {
                            Text("Install")
                        }
                        OutlinedButton(onClick = viewModel::resetDownload, modifier = Modifier.weight(1f)) {
                            Text("Re-download")
                        }
                    }
                }
                is DownloadState.Error -> {
                    Text(
                        text = "Error: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = viewModel::download, modifier = Modifier.fillMaxWidth()) {
                        Text("Retry")
                    }
                }
            }

            if (uiState.releaseNotes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("What's new", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                uiState.releaseNotes.forEach { note ->
                    ReleaseNoteBlock(note)
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ReleaseNoteBlock(note: AppUpdateReleaseNote) {
    Text(text = note.versionName, style = MaterialTheme.typography.titleSmall)
    Text(
        text = note.releasedAt.take(10),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(4.dp))
    note.features.forEach { Text("• $it") }
    note.upgrades.forEach { Text("↑ $it") }
    note.fixes.forEach { Text("✓ $it") }
}
