package com.mari.app.ui.screens.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
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
import com.mari.app.ui.dialogs.ExecutingConflictDialog
import com.mari.app.ui.util.rememberCountdown
import com.mari.shared.domain.DeadlineReminder
import com.mari.shared.domain.DueDateResolver
import com.mari.shared.domain.DueKind
import com.mari.shared.domain.DuePreset
import com.mari.shared.domain.Task
import com.mari.shared.domain.TaskPriority
import com.mari.shared.domain.TaskStatus
import com.mari.shared.domain.TaskValidation
import com.mari.shared.domain.preset
import com.mari.shared.domain.toSimpleDueKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DISPLAYED_PRESETS_EDIT = DuePreset.entries.filter { it != DuePreset.MONTH_YEAR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTaskScreen(
    onNavigateUp: () -> Unit,
    viewModel: EditTaskViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.isSaved, uiState.isDeleted) {
        if (uiState.isSaved || uiState.isDeleted) onNavigateUp()
    }

    val editError = uiState.editError
    LaunchedEffect(editError) {
        if (editError != null) {
            snackbarHostState.showSnackbar(editError)
            viewModel.onClearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Edit task") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val task = uiState.task
        if (task == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            EditTaskFormContent(
                task = task,
                reminderTemplates = uiState.reminderTemplates,
                priorityColors = uiState.priorityColors,
                executingConflict = uiState.executingConflict,
                onSave = viewModel::onSave,
                onDelete = viewModel::onDelete,
                onConflictFinish = viewModel::onConflictFinish,
                onConflictPause = viewModel::onConflictPause,
                onDismissConflict = viewModel::onDismissConflict,
                modifier = Modifier
                    .padding(padding)
                    .navigationBarsPadding(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditTaskFormContent(
    task: Task,
    reminderTemplates: List<DeadlineReminder>,
    priorityColors: Map<TaskPriority, String?>,
    executingConflict: ExecutingConflict?,
    onSave: (
        name: String,
        description: String,
        status: TaskStatus,
        dueAt: Instant?,
        dueKind: DueKind?,
        reminders: List<DeadlineReminder>,
        priority: TaskPriority,
        colorHex: String?,
        customColorHex: String?,
        useCustomColor: Boolean,
    ) -> Unit,
    onDelete: () -> Unit,
    onConflictFinish: () -> Unit,
    onConflictPause: () -> Unit,
    onDismissConflict: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialPriorityColor = priorityColors[task.priority]
    val initialCustomColor = task.customColorHex
        ?: task.colorHex?.takeIf { it != initialPriorityColor }

    var name by remember(task.id) { mutableStateOf(task.name) }
    var description by remember(task.id) { mutableStateOf(task.description) }
    var priority by remember(task.id) { mutableStateOf(task.priority) }
    var customColorHex by remember(task.id) { mutableStateOf(initialCustomColor.orEmpty()) }
    var useCustomColor by remember(task.id) {
        mutableStateOf(task.useCustomColor || initialCustomColor != null)
    }
    var colorHex by remember(task.id) {
        mutableStateOf(if (useCustomColor) customColorHex else priorityColors[priority].orEmpty())
    }
    var duePreset by remember(task.id) {
        mutableStateOf(task.dueKind?.preset?.takeIf { it != DuePreset.MONTH_YEAR })
    }
    var dueDateText by remember(task.id) {
        mutableStateOf(task.dueAt?.atZone(ZoneId.systemDefault())?.toLocalDate()?.toString().orEmpty())
    }
    var dueTimeText by remember(task.id) {
        mutableStateOf(
            task.dueAt?.atZone(ZoneId.systemDefault())
                ?.toLocalTime()?.withSecond(0)?.withNano(0)?.toString().orEmpty(),
        )
    }
    var nameError by remember(task.id) { mutableStateOf<String?>(null) }
    var descriptionError by remember(task.id) { mutableStateOf<String?>(null) }
    var formError by remember(task.id) { mutableStateOf<String?>(null) }
    var showStatusSheet by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deadlineDropdownExpanded by remember { mutableStateOf(false) }
    var pendingStatus by remember(task.id) { mutableStateOf(task.status) }
    var selectedReminderOffsets by remember(task.id) {
        mutableStateOf(task.deadlineReminders.map { it.offsetSeconds }.toSet())
    }

    fun applyPriority(nextPriority: TaskPriority) {
        priority = nextPriority
        if (!useCustomColor) colorHex = priorityColors[nextPriority].orEmpty()
    }

    fun applyUseCustomColor(enabled: Boolean) {
        useCustomColor = enabled
        colorHex = if (enabled) customColorHex else priorityColors[priority].orEmpty()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Delete button row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete task",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it; nameError = null },
            label = { Text("Name") },
            isError = nameError != null,
            supportingText = nameError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = description,
            onValueChange = { description = it; descriptionError = null },
            label = { Text("Notes") },
            isError = descriptionError != null,
            supportingText = descriptionError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Due date dropdown
        val deadlineLabel = duePreset?.editLabel() ?: "None"
        ExposedDropdownMenuBox(
            expanded = deadlineDropdownExpanded,
            onExpandedChange = { deadlineDropdownExpanded = it },
        ) {
            OutlinedTextField(
                value = deadlineLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Due date") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deadlineDropdownExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = deadlineDropdownExpanded,
                onDismissRequest = { deadlineDropdownExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("None") },
                    onClick = {
                        duePreset = null
                        dueDateText = ""
                        dueTimeText = ""
                        deadlineDropdownExpanded = false
                    },
                )
                DISPLAYED_PRESETS_EDIT.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(preset.editLabel()) },
                        onClick = {
                            duePreset = preset
                            deadlineDropdownExpanded = false
                            if (preset == DuePreset.SPECIFIC_DAY) {
                                showDatePicker = true
                            }
                        },
                    )
                }
            }
        }

        if (duePreset == DuePreset.SPECIFIC_DAY && dueDateText.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val timeDisplay = dueTimeText.ifBlank { "08:00" }
                Text(
                    text = editFormatDateTimeSummary(dueDateText, timeDisplay),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showDatePicker = true }) { Text("Change") }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Priority",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = priority.ordinal.toFloat(),
            onValueChange = {
                applyPriority(TaskPriority.entries[it.toInt().coerceIn(0, TaskPriority.entries.lastIndex)])
            },
            valueRange = 0f..TaskPriority.entries.lastIndex.toFloat(),
            steps = TaskPriority.entries.size - 2,
        )
        Text(
            text = priority.editLabel(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = colorHex,
            onValueChange = {},
            readOnly = true,
            label = { Text("Task color") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            ColorUtils.parseHexOrFallback(colorHex, Color(0xFFB0BEC5)),
                        ),
                )
            },
            trailingIcon = {
                TextButton(onClick = { showColorPicker = true }) { Text("Pick") }
            },
        )
        if (customColorHex.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = useCustomColor,
                    onCheckedChange = ::applyUseCustomColor,
                )
                Text("Use custom color")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "Due date notifications",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        reminderTemplates.forEach { reminder ->
            Row {
                Checkbox(
                    checked = reminder.offsetSeconds in selectedReminderOffsets,
                    onCheckedChange = { checked ->
                        selectedReminderOffsets = if (checked) {
                            selectedReminderOffsets + reminder.offsetSeconds
                        } else {
                            selectedReminderOffsets - reminder.offsetSeconds
                        }
                    },
                )
                Text(
                    reminder.label ?: "${reminder.offsetSeconds}s",
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = pendingStatus.editLabel(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Status") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                TextButton(onClick = { showStatusSheet = true }) { Text("Change") }
            },
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = {
                val validatedName = TaskValidation.validateName(name)
                val validatedDescription = TaskValidation.validateDescription(description)
                if (validatedName.isFailure || validatedDescription.isFailure) {
                    nameError = validatedName.exceptionOrNull()?.message
                    descriptionError = validatedDescription.exceptionOrNull()?.message
                    return@Button
                }
                val effectiveTimeText = if (duePreset == DuePreset.SPECIFIC_DAY) {
                    dueTimeText.ifBlank { "08:00" }
                } else {
                    dueTimeText
                }
                val dueSelection = runCatching {
                    when (duePreset) {
                        null -> null
                        DuePreset.SPECIFIC_DAY -> {
                            val kind = DueKind.SpecificDay(
                                dateIso = LocalDate.parse(dueDateText).toString(),
                                timeHhmm = effectiveTimeText.takeIf { it.isNotBlank() },
                            )
                            kind to DueDateResolver.resolve(kind, Instant.now(), ZoneId.systemDefault())
                        }
                        DuePreset.MONTH_YEAR -> null
                        else -> duePreset!!.toSimpleDueKind()?.let { kind ->
                            kind to DueDateResolver.resolve(kind, Instant.now(), ZoneId.systemDefault())
                        }
                    }
                }.getOrNull()
                if (duePreset != null && dueSelection == null) {
                    formError = "Enter a valid due date"
                    return@Button
                }
                if (dueSelection != null && colorHex.isBlank()) {
                    formError = "Choose a color for tasks with due dates"
                    return@Button
                }
                onSave(
                    validatedName.getOrThrow(),
                    validatedDescription.getOrThrow(),
                    pendingStatus,
                    dueSelection?.second,
                    dueSelection?.first,
                    reminderTemplates.filter { it.offsetSeconds in selectedReminderOffsets },
                    priority,
                    colorHex.ifBlank { null },
                    customColorHex.ifBlank { null },
                    useCustomColor,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save")
        }
        formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showStatusSheet) {
        val innerSheetState = rememberModalBottomSheetState()
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

    if (showDatePicker) {
        AppSingleDatePickerDialog(
            currentIsoDate = dueDateText,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                dueDateText = it
                showDatePicker = false
                showTimePicker = true
            },
        )
    }

    if (showTimePicker) {
        val initialHour = dueTimeText.parseEditHour() ?: 8
        val initialMinute = dueTimeText.parseEditMinute() ?: 0
        val timeState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dueTimeText = "%02d:%02d".format(timeState.hour, timeState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Skip") }
            },
            title = { Text("Pick time (optional)") },
            text = { TimePicker(state = timeState) },
        )
    }

    if (showColorPicker) {
        ColourPickerDialog(
            initialColorHex = colorHex.ifBlank { "#5C6BC0" },
            showAlphaField = false,
            onDismiss = { showColorPicker = false },
            onConfirm = {
                customColorHex = it
                useCustomColor = true
                colorHex = it
                showColorPicker = false
            },
        )
    }

    if (showDeleteConfirm) {
        val countdown by rememberCountdown(4_000L)
        val ready = countdown == 0L
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete task permanently?") },
            text = {
                Text(
                    if (ready) "This cannot be undone."
                    else "This cannot be undone. You can confirm in ${(countdown / 1000L) + 1}s.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    enabled = ready,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete forever") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    executingConflict?.let { conflict ->
        ExecutingConflictDialog(
            executingDescription = conflict.existing.name,
            onFinish = onConflictFinish,
            onPause = onConflictPause,
            onCancel = onDismissConflict,
        )
    }
}

private fun TaskStatus.editLabel(): String = when (this) {
    TaskStatus.TO_BE_DONE -> "To Do"
    TaskStatus.PAUSED -> "Paused"
    TaskStatus.EXECUTING -> "Executing"
    TaskStatus.COMPLETED -> "Completed"
    TaskStatus.DISCARDED -> "Discarded"
    else -> "To Do"
}

private fun TaskPriority.editLabel(): String = when (this) {
    TaskPriority.LOW -> "Low"
    TaskPriority.NORMAL -> "Normal"
    TaskPriority.HIGH -> "High"
    TaskPriority.VERY_HIGH -> "Very high"
}

private fun DuePreset.editLabel(): String = when (this) {
    DuePreset.SPECIFIC_DAY -> "Specific day"
    DuePreset.THIS_WEEK -> "This week"
    DuePreset.NEXT_WEEK -> "Next week"
    DuePreset.THIS_MONTH -> "This month"
    DuePreset.NEXT_MONTH -> "Next month"
    DuePreset.MONTH_YEAR -> "Month + year"
}

private fun editFormatDateTimeSummary(dateIso: String, timeHhmm: String): String =
    runCatching {
        val date = LocalDate.parse(dateIso)
        val formatted = date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
        "$formatted at $timeHhmm"
    }.getOrDefault("$dateIso at $timeHhmm")

private fun String.parseEditHour(): Int? =
    runCatching { split(":").first().toInt().coerceIn(0, 23) }.getOrNull()

private fun String.parseEditMinute(): Int? =
    runCatching { split(":").getOrNull(1)?.toInt()?.coerceIn(0, 59) }.getOrNull()
