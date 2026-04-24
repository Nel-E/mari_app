package com.mari.app.ui.screens.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import com.mari.app.settings.ThemeMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mari.app.BuildConfig
import com.mari.app.ui.common.ColorUtils
import com.mari.app.ui.common.colourpicker.ColourPickerDialog
import com.mari.shared.domain.TaskPriority
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
    onNavigateToUpdate: () -> Unit = {},
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
            SectionTitle("Appearance")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = uiState.themeMode == mode,
                        onClick = { viewModel.onThemeModeChange(mode) },
                        label = {
                            Text(
                                when (mode) {
                                    ThemeMode.LIGHT -> "Light"
                                    ThemeMode.DARK -> "Dark"
                                    ThemeMode.SYSTEM -> "System"
                                },
                            )
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SectionTitle("Task Priority Colors")
            PriorityColorsSection(
                colors = uiState.priorityColors,
                onColorChange = viewModel::onPriorityColorChange,
            )
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
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
            HorizontalDivider()
            SectionTitle("Do Not Disturb")
            QuietHoursSection(
                startHour = uiState.quietStartHour,
                startMinute = uiState.quietStartMinute,
                endHour = uiState.quietEndHour,
                endMinute = uiState.quietEndMinute,
                onRangeChange = viewModel::onQuietHoursChange,
            )
            HorizontalDivider()
            SectionTitle("Daily Task Reminder")
            DailyNudgeSection(
                enabled = uiState.dailyNudgeEnabled,
                hour = uiState.dailyNudgeHour,
                minute = uiState.dailyNudgeMinute,
                onEnabledChange = viewModel::onDailyNudgeEnabledChange,
                onTimeChange = viewModel::onDailyNudgeTimeChange,
            )
            HorizontalDivider()
            SectionTitle("Weekly Backup")
            ListItem(
                headlineContent = { Text("Backup Info") },
                supportingContent = {
                    Text("Last: ${uiState.backupInfo.lastBackupLabel}\nNext: ${uiState.backupInfo.nextBackupLabel}")
                },
            )
            AppUpdateSection(
                autoCheckEnabled = uiState.updateAutoCheckEnabled,
                track = uiState.updateTrack,
                availableUpdate = uiState.availableUpdate,
                onAutoCheckChange = viewModel::onUpdateAutoCheckChange,
                onTrackChange = viewModel::onUpdateTrackChange,
                onCheckNow = viewModel::onCheckNow,
                onViewUpdate = onNavigateToUpdate,
            )
            HorizontalDivider()
            SectionTitle("About")
            ListItem(
                headlineContent = { Text("Version") },
                trailingContent = { Text(BuildConfig.VERSION_NAME) },
            )
            ListItem(
                headlineContent = { Text("Build time") },
                trailingContent = {
                    val formatted = remember {
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                            .format(Date(BuildConfig.BUILD_TIME_MS))
                    }
                    Text(
                        text = formatted,
                        textAlign = TextAlign.End,
                    )
                },
            )
        }
    }
}

@Composable
private fun PriorityColorsSection(
    colors: Map<TaskPriority, String?>,
    onColorChange: (TaskPriority, String?) -> Unit,
) {
    var editingPriority by remember { mutableStateOf<TaskPriority?>(null) }

    TaskPriority.entries.forEach { priority ->
        val colorHex = colors[priority]
        ListItem(
            headlineContent = { Text(priority.label()) },
            supportingContent = { Text(colorHex ?: "No color") },
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(ColorUtils.parseHexOrFallback(colorHex, Color.Transparent)),
                )
            },
            trailingContent = {
                Row {
                    TextButton(onClick = { editingPriority = priority }) { Text("Pick") }
                    TextButton(onClick = { onColorChange(priority, null) }) { Text("Reset") }
                }
            },
        )
    }

    editingPriority?.let { priority ->
        ColourPickerDialog(
            initialColorHex = colors[priority] ?: "#5C6BC0",
            title = "${priority.label()} color",
            showAlphaField = false,
            onDismiss = { editingPriority = null },
            onConfirm = { colorHex ->
                onColorChange(priority, colorHex)
                editingPriority = null
            },
        )
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

private fun TaskPriority.label(): String = when (this) {
    TaskPriority.LOW -> "Low"
    TaskPriority.NORMAL -> "Normal"
    TaskPriority.HIGH -> "High"
    TaskPriority.VERY_HIGH -> "Very high"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyNudgeSection(
    enabled: Boolean,
    hour: Int,
    minute: Int,
    onEnabledChange: (Boolean) -> Unit,
    onTimeChange: (Int, Int) -> Unit,
) {
    var showTimePicker by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text("Daily task reminder") },
        supportingContent = { Text("You'll be nudged once a day to continue or pick a task.") },
        trailingContent = {
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        },
    )
    ListItem(
        headlineContent = { Text("Reminder time") },
        supportingContent = { Text("%02d:%02d".format(hour, minute)) },
        trailingContent = {
            TextButton(onClick = { showTimePicker = true }) { Text("Change") }
        },
    )

    if (showTimePicker) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(state.hour, state.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = state) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuietHoursSection(
    startHour: Int,
    startMinute: Int,
    endHour: Int,
    endMinute: Int,
    onRangeChange: (startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) -> Unit,
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text("From") },
        supportingContent = { Text("%02d:%02d".format(startHour, startMinute)) },
        trailingContent = {
            TextButton(onClick = { showStartPicker = true }) { Text("Change") }
        },
    )
    ListItem(
        headlineContent = { Text("To") },
        supportingContent = { Text("%02d:%02d".format(endHour, endMinute)) },
        trailingContent = {
            TextButton(onClick = { showEndPicker = true }) { Text("Change") }
        },
    )

    if (showStartPicker) {
        val state = rememberTimePickerState(initialHour = startHour, initialMinute = startMinute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onRangeChange(state.hour, state.minute, endHour, endMinute)
                    showStartPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = state) },
        )
    }

    if (showEndPicker) {
        val state = rememberTimePickerState(initialHour = endHour, initialMinute = endMinute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onRangeChange(startHour, startMinute, state.hour, state.minute)
                    showEndPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = state) },
        )
    }
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
