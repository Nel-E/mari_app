package com.mari.app.ui.screens.tasks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mari.shared.domain.DeadlineReminder
import com.mari.shared.domain.DueDateResolver
import com.mari.shared.domain.DueKind
import com.mari.shared.domain.DuePreset
import com.mari.shared.domain.Task
import com.mari.shared.domain.TaskStatus
import com.mari.shared.domain.TaskValidation
import com.mari.shared.domain.preset
import com.mari.shared.domain.toSimpleDueKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditTaskSheet(
    task: Task,
    sheetState: SheetState,
    reminderTemplates: List<DeadlineReminder>,
    onSave: (name: String, description: String, status: TaskStatus, dueAt: Instant?, dueKind: DueKind?, reminders: List<DeadlineReminder>, colorHex: String?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(task.id) { mutableStateOf(task.name) }
    var description by remember(task.id) { mutableStateOf(task.description) }
    var colorHex by remember(task.id) { mutableStateOf(task.colorHex.orEmpty()) }
    var duePreset by remember(task.id) { mutableStateOf(task.dueKind?.preset) }
    var dueDateText by remember(task.id) { mutableStateOf(task.dueAt?.atZone(ZoneId.systemDefault())?.toLocalDate()?.toString().orEmpty()) }
    var dueTimeText by remember(task.id) { mutableStateOf(task.dueAt?.atZone(ZoneId.systemDefault())?.toLocalTime()?.withSecond(0)?.withNano(0)?.toString().orEmpty()) }
    var dueMonthText by remember(task.id) { mutableStateOf((task.dueKind as? DueKind.MonthYear)?.month?.toString().orEmpty()) }
    var dueYearText by remember(task.id) { mutableStateOf((task.dueKind as? DueKind.MonthYear)?.year?.toString().orEmpty()) }
    var nameError by remember(task.id) { mutableStateOf<String?>(null) }
    var descriptionError by remember(task.id) { mutableStateOf<String?>(null) }
    var formError by remember(task.id) { mutableStateOf<String?>(null) }
    var showStatusSheet by remember { mutableStateOf(false) }
    var pendingStatus by remember(task.id) { mutableStateOf(task.status) }
    var selectedReminderOffsets by remember(task.id) { mutableStateOf(task.deadlineReminders.map { it.offsetSeconds }.toSet()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Text("Edit task", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
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
            Text("Deadline")
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow {
                FilterChip(selected = duePreset == null, onClick = { duePreset = null }, label = { Text("None") })
                DuePreset.entries.forEach { preset ->
                    FilterChip(selected = duePreset == preset, onClick = { duePreset = preset }, label = { Text(preset.label()) })
                }
            }
            if (duePreset == DuePreset.SPECIFIC_DAY) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = dueDateText,
                    onValueChange = { dueDateText = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = dueTimeText,
                    onValueChange = { dueTimeText = it },
                    label = { Text("Time (HH:MM, optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            if (duePreset == DuePreset.MONTH_YEAR) {
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = dueMonthText,
                        onValueChange = { dueMonthText = it },
                        label = { Text("Month") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = dueYearText,
                        onValueChange = { dueYearText = it },
                        label = { Text("Year") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = colorHex,
                onValueChange = { colorHex = it },
                label = { Text("Color hex") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(12.dp))
            reminderTemplates.forEach { reminder ->
                Row {
                    Checkbox(
                        checked = reminder.offsetSeconds in selectedReminderOffsets,
                        onCheckedChange = { checked ->
                            selectedReminderOffsets = if (checked) selectedReminderOffsets + reminder.offsetSeconds else selectedReminderOffsets - reminder.offsetSeconds
                        },
                    )
                    Text(reminder.label ?: "${reminder.offsetSeconds}s")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = pendingStatus.label(),
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
                    val dueSelection = runCatching {
                        when (duePreset) {
                            null -> null
                            DuePreset.SPECIFIC_DAY -> {
                                val kind = DueKind.SpecificDay(
                                    dateIso = LocalDate.parse(dueDateText).toString(),
                                    timeHhmm = dueTimeText.takeIf { it.isNotBlank() },
                                )
                                kind to DueDateResolver.resolve(kind, Instant.now(), ZoneId.systemDefault())
                            }
                            DuePreset.MONTH_YEAR -> {
                                val kind = DueKind.MonthYear(month = dueMonthText.toInt(), year = dueYearText.toInt())
                                kind to DueDateResolver.resolve(kind, Instant.now(), ZoneId.systemDefault())
                            }
                            else -> duePreset!!.toSimpleDueKind()?.let { kind ->
                                kind to DueDateResolver.resolve(kind, Instant.now(), ZoneId.systemDefault())
                            }
                        }
                    }.getOrNull()
                    if (duePreset != null && dueSelection == null) {
                        formError = "Enter a valid deadline"
                        return@Button
                    }
                    if (dueSelection != null && colorHex.isBlank()) {
                        formError = "Choose a color for tasks with deadlines"
                        return@Button
                    }
                    onSave(
                        validatedName.getOrThrow(),
                        validatedDescription.getOrThrow(),
                        pendingStatus,
                        dueSelection?.second,
                        dueSelection?.first,
                        reminderTemplates.filter { it.offsetSeconds in selectedReminderOffsets },
                        colorHex.ifBlank { null },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
            formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Delete")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showStatusSheet) {
        val innerSheetState = androidx.compose.material3.rememberModalBottomSheetState()
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
}

private fun TaskStatus.label(): String = when (this) {
    TaskStatus.TO_BE_DONE -> "To Do"
    TaskStatus.PAUSED -> "Paused"
    TaskStatus.EXECUTING -> "Executing"
    TaskStatus.QUEUED -> "Queued"
    TaskStatus.COMPLETED -> "Completed"
    TaskStatus.DISCARDED -> "Discarded"
}

private fun DuePreset.label(): String = when (this) {
    DuePreset.SPECIFIC_DAY -> "Specific day"
    DuePreset.THIS_WEEK -> "This week"
    DuePreset.NEXT_WEEK -> "Next week"
    DuePreset.THIS_MONTH -> "This month"
    DuePreset.NEXT_MONTH -> "Next month"
    DuePreset.MONTH_YEAR -> "Month + year"
}
