package com.mari.app.ui.screens.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Slider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mari.app.ui.common.AppSingleDatePickerDialog
import com.mari.app.ui.common.ColorUtils
import com.mari.app.ui.common.colourpicker.ColourPickerDialog
import com.mari.shared.domain.DuePreset
import com.mari.shared.domain.TaskPriority

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTaskScreen(
    onNavigateUp: () -> Unit,
    viewModel: AddTaskViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) onNavigateUp()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Task") },
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
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Task name") },
                isError = uiState.nameError != null,
                supportingText = uiState.nameError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = uiState.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Notes") },
                isError = uiState.descriptionError != null,
                supportingText = uiState.descriptionError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Deadline")
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.duePreset == null,
                    onClick = { viewModel.onDuePresetChange(null) },
                    label = { Text("None") },
                )
                DuePreset.entries.forEach { preset ->
                    FilterChip(
                        selected = uiState.duePreset == preset,
                        onClick = { viewModel.onDuePresetChange(preset) },
                        label = { Text(preset.label()) },
                    )
                }
            }
            if (uiState.duePreset == DuePreset.SPECIFIC_DAY) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.dueDateText,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = { showDatePicker = true }) { Text("Pick") }
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.dueTimeText,
                    onValueChange = viewModel::onDueTimeChange,
                    label = { Text("Time (HH:MM, optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            if (uiState.duePreset == DuePreset.MONTH_YEAR) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.dueMonthText,
                        onValueChange = viewModel::onDueMonthChange,
                        label = { Text("Month") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = uiState.dueYearText,
                        onValueChange = viewModel::onDueYearChange,
                        label = { Text("Year") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
            }
            uiState.duePreview?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Due: $it")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Priority")
            Slider(
                value = uiState.priority.ordinal.toFloat(),
                onValueChange = {
                    viewModel.onPriorityChange(
                        TaskPriority.entries[it.toInt().coerceIn(0, TaskPriority.entries.lastIndex)]
                    )
                },
                valueRange = 0f..TaskPriority.entries.lastIndex.toFloat(),
                steps = TaskPriority.entries.size - 2,
            )
            Text(uiState.priority.label())
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = uiState.colorHex,
                onValueChange = {},
                readOnly = true,
                label = { Text("Task color") },
                isError = uiState.colorError != null,
                supportingText = uiState.colorError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                ColorUtils.parseHexOrFallback(
                                    uiState.colorHex,
                                    Color(0xFFB0BEC5),
                                ),
                            ),
                    ) {
                    }
                },
                trailingIcon = {
                    TextButton(onClick = { showColorPicker = true }) { Text("Pick") }
                },
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Reminder templates")
            Spacer(modifier = Modifier.height(8.dp))
            uiState.reminderTemplates.forEach { reminder ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = reminder.offsetSeconds in uiState.selectedReminderOffsets,
                        onCheckedChange = { checked -> viewModel.onReminderToggle(reminder.offsetSeconds, checked) },
                    )
                    Text(reminder.label ?: "${reminder.offsetSeconds}s")
                }
            }
            uiState.formError?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = viewModel::save,
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (uiState.isSaving) "Saving..." else "Save Task")
            }
        }
    }

    if (showDatePicker) {
        AppSingleDatePickerDialog(
            currentIsoDate = uiState.dueDateText,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                viewModel.onDueDateChange(it)
                showDatePicker = false
            },
        )
    }

    if (showColorPicker) {
        ColourPickerDialog(
            initialColorHex = uiState.colorHex.ifBlank { "#5C6BC0" },
            showAlphaField = false,
            onDismiss = { showColorPicker = false },
            onConfirm = {
                viewModel.onColorChange(it)
                showColorPicker = false
            },
        )
    }
}

private fun TaskPriority.label(): String = when (this) {
    TaskPriority.LOW -> "Low"
    TaskPriority.NORMAL -> "Normal"
    TaskPriority.HIGH -> "High"
    TaskPriority.VERY_HIGH -> "Very high"
}

private fun DuePreset.label(): String = when (this) {
    DuePreset.SPECIFIC_DAY -> "Specific day"
    DuePreset.THIS_WEEK -> "This week"
    DuePreset.NEXT_WEEK -> "Next week"
    DuePreset.THIS_MONTH -> "This month"
    DuePreset.NEXT_MONTH -> "Next month"
    DuePreset.MONTH_YEAR -> "Month + year"
}
