package com.mari.app.ui.screens.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.onFolderPicked(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .padding(padding),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            SectionTitle("Storage")
            ListItem(
                headlineContent = { Text("Storage Folder") },
                supportingContent = { Text(uiState.storageFolderLabel) },
                trailingContent = {
                    TextButton(onClick = { folderPicker.launch(null) }) {
                        Text("Change")
                    }
                },
            )
            HorizontalDivider()
            SectionTitle("Shake")
            SliderItem(
                label = "Shake strength",
                valueLabel = uiState.shakeStrength.toInt().toString(),
                value = uiState.shakeStrength,
                range = 10f..30f,
                onValueChange = viewModel::onShakeStrengthChange,
            )
            SliderItem(
                label = "Shake duration",
                valueLabel = "${uiState.shakeDurationMs} ms",
                value = uiState.shakeDurationMs.toFloat(),
                range = 100f..1000f,
                onValueChange = viewModel::onShakeDurationChange,
            )
            ListItem(
                headlineContent = { Text("Shake vibration") },
                trailingContent = {
                    Switch(
                        checked = uiState.shakeVibrate,
                        onCheckedChange = viewModel::onShakeVibrateChange,
                    )
                },
            )
            HorizontalDivider()
            SectionTitle("Execution Reminder")
            ListItem(
                headlineContent = { Text("Enabled") },
                trailingContent = {
                    Switch(
                        checked = uiState.reminderEnabled,
                        onCheckedChange = viewModel::onReminderEnabledChange,
                    )
                },
            )
            SliderItem(
                label = "Frequency",
                valueLabel = "${uiState.reminderIntervalMinutes} min",
                value = uiState.reminderIntervalMinutes.toFloat(),
                range = 1f..1440f,
                onValueChange = viewModel::onReminderIntervalChange,
            )
            ListItem(
                headlineContent = { Text("Reminder vibration") },
                trailingContent = {
                    Switch(
                        checked = uiState.reminderVibrate,
                        onCheckedChange = viewModel::onReminderVibrateChange,
                    )
                },
            )
            ListItem(
                headlineContent = { Text("Quiet hours") },
                supportingContent = {
                    Text(
                        text = uiState.quietHoursLabel,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                },
                trailingContent = {
                    Row {
                        TextButton(onClick = { viewModel.shiftQuietHours(-1) }) { Text("-1h") }
                        TextButton(onClick = { viewModel.shiftQuietHours(1) }) { Text("+1h") }
                    }
                },
            )
            HorizontalDivider()
            SectionTitle("Weekly Backup")
            ListItem(
                headlineContent = { Text("Backup Info") },
                supportingContent = {
                    Text("Last: ${uiState.backupInfo.lastBackupLabel}\nNext: ${uiState.backupInfo.nextBackupLabel}")
                },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SliderItem(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
            )
        },
        trailingContent = { Text(valueLabel) },
    )
}
