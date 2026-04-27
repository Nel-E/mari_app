package com.mari.app.ui.screens.tasks

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mari.app.reminders.DeadlineReminderScheduler
import com.mari.app.settings.PhoneSettings
import com.mari.app.settings.SettingsReader
import com.mari.shared.data.repository.TaskRepository
import com.mari.shared.domain.Clock
import com.mari.shared.domain.DeadlineReminder
import com.mari.shared.domain.DeviceId
import com.mari.shared.domain.DueKind
import com.mari.shared.domain.ExecutionRules
import com.mari.shared.domain.Task
import com.mari.shared.domain.TaskColor
import com.mari.shared.domain.TaskPriority
import com.mari.shared.domain.TaskStatus
import com.mari.shared.domain.TaskValidation
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExecutingConflict(
    val existing: Task,
    val incoming: Task,
)

data class EditTaskUiState(
    val task: Task? = null,
    val reminderTemplates: List<DeadlineReminder> = PhoneSettings.DEFAULT_DEADLINE_REMINDER_TEMPLATES,
    val priorityColors: Map<TaskPriority, String?> = PhoneSettings.DEFAULT_PRIORITY_COLORS,
    val executingConflict: ExecutingConflict? = null,
    val editError: String? = null,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
)

@HiltViewModel
class EditTaskViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TaskRepository,
    settingsRepository: SettingsReader,
    private val deadlineReminderScheduler: DeadlineReminderScheduler,
    private val clock: Clock,
) : ViewModel() {

    val taskId: String = checkNotNull(savedStateHandle["taskId"])

    private val _uiState = MutableStateFlow(EditTaskUiState())
    val uiState: StateFlow<EditTaskUiState> = _uiState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditTaskUiState())

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(task = repository.getTasks().firstOrNull { t -> t.id == taskId }) }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        reminderTemplates = settings.deadlineReminderTemplates,
                        priorityColors = settings.priorityColors,
                    )
                }
            }
        }
    }

    fun onClearError() {
        _uiState.update { it.copy(editError = null) }
    }

    fun onSave(
        name: String,
        description: String,
        newStatus: TaskStatus,
        dueAt: Instant?,
        dueKind: DueKind?,
        reminders: List<DeadlineReminder>,
        priority: TaskPriority,
        colorHex: String?,
        customColorHex: String?,
        useCustomColor: Boolean,
    ) {
        viewModelScope.launch {
            if (TaskValidation.findDuplicateName(repository.getTasks(), name, excludeId = taskId) != null) {
                _uiState.update { it.copy(editError = "A task with this name already exists") }
                return@launch
            }
            _uiState.update { it.copy(editError = null) }

            val currentTasks = repository.getTasks()
            val normalizedColor = colorHex?.let { TaskColor.parse(it).getOrNull()?.hex }
            val normalizedCustomColor = customColorHex?.let { TaskColor.parse(it).getOrNull()?.hex }
            val shouldUseCustomColor = useCustomColor && normalizedCustomColor != null

            if (newStatus == TaskStatus.EXECUTING) {
                val existing = ExecutionRules.currentlyExecuting(currentTasks)
                if (existing != null && existing.id != taskId) {
                    _uiState.update {
                        it.copy(
                            executingConflict = ExecutingConflict(
                                existing = existing,
                                incoming = currentTasks.first { t -> t.id == taskId }.copy(
                                    name = name,
                                    description = description,
                                    dueAt = dueAt,
                                    dueKind = dueKind,
                                    deadlineReminders = reminders,
                                    priority = priority,
                                    colorHex = normalizedColor,
                                    customColorHex = normalizedCustomColor,
                                    useCustomColor = shouldUseCustomColor,
                                ),
                            ),
                        )
                    }
                    return@launch
                }
            }

            val result = repository.update { tasks ->
                tasks.map { task ->
                    if (task.id != taskId) task
                    else ExecutionRules.applyStatusChange(
                        task = ExecutionRules.updateTaskMetadata(
                            task = task,
                            clock = clock,
                            deviceId = DeviceId.PHONE,
                            name = name,
                            description = description,
                            dueAt = dueAt,
                            dueKind = dueKind,
                            deadlineReminders = reminders,
                            priority = priority,
                            colorHex = normalizedColor,
                            customColorHex = normalizedCustomColor,
                            useCustomColor = shouldUseCustomColor,
                        ).copy(
                            version = task.version,
                            updatedAt = task.updatedAt,
                            lastModifiedBy = task.lastModifiedBy,
                        ),
                        newStatus = newStatus,
                        clock = clock,
                        deviceId = DeviceId.PHONE,
                    )
                }
            }
            if (result.isSuccess) {
                deadlineReminderScheduler.cancel(taskId)
                repository.getTasks()
                    .firstOrNull { it.id == taskId }
                    ?.takeIf { it.dueAt != null }
                    ?.let(deadlineReminderScheduler::schedule)
                _uiState.update { it.copy(isSaved = true) }
            } else {
                _uiState.update { it.copy(editError = "Save failed: ${result.exceptionOrNull()?.message ?: "Storage error"}") }
            }
        }
    }

    fun onDelete() {
        viewModelScope.launch {
            val result = repository.delete(taskId)
            if (result.isSuccess) {
                deadlineReminderScheduler.cancel(taskId)
                _uiState.update { it.copy(isDeleted = true) }
            } else {
                Log.e("EditTaskVM", "Delete failed for $taskId: ${result.exceptionOrNull()}")
                _uiState.update { it.copy(editError = "Delete failed: ${result.exceptionOrNull()?.message ?: "Storage error"}") }
            }
        }
    }

    fun onDismissConflict() {
        _uiState.update { it.copy(executingConflict = null) }
    }

    fun onConflictFinish() {
        resolveConflict(TaskStatus.COMPLETED)
    }

    fun onConflictPause() {
        resolveConflict(TaskStatus.PAUSED)
    }

    private fun resolveConflict(existingStatus: TaskStatus) {
        val conflict = _uiState.value.executingConflict ?: return
        _uiState.update { it.copy(executingConflict = null) }
        viewModelScope.launch {
            repository.update { tasks ->
                tasks.map { task ->
                    when (task.id) {
                        conflict.existing.id -> ExecutionRules.applyStatusChange(
                            task,
                            existingStatus,
                            clock,
                            DeviceId.PHONE,
                        )
                        conflict.incoming.id -> ExecutionRules.applyStatusChange(
                            conflict.incoming.copy(
                                version = task.version,
                                updatedAt = task.updatedAt,
                                lastModifiedBy = task.lastModifiedBy,
                            ),
                            TaskStatus.EXECUTING,
                            clock,
                            DeviceId.PHONE,
                        )
                        else -> task
                    }
                }
            }
            _uiState.update { it.copy(isSaved = true) }
        }
    }
}
