package com.mari.app.ui.screens.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mari.app.reminders.DeadlineReminderScheduler
import com.mari.app.settings.SettingsReader
import com.mari.shared.data.repository.TaskRepository
import com.mari.shared.domain.Clock
import com.mari.shared.domain.DeadlineReminder
import com.mari.shared.domain.DeviceId
import com.mari.shared.domain.DueKind
import com.mari.shared.domain.ExecutionRules
import com.mari.shared.domain.Task
import com.mari.shared.domain.TaskListing
import com.mari.shared.domain.TaskStatus
import com.mari.shared.domain.TaskValidation
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AllTasksUiState(
    val tasks: List<Task> = emptyList(),
    val filterState: TaskFilterState = TaskFilterState(),
    val selectedTask: Task? = null,
    val pendingDeleteTask: Task? = null,
    val executingConflict: ExecutingConflict? = null,
)

data class ExecutingConflict(
    val existing: Task,
    val incoming: Task,
)

@HiltViewModel
class AllTasksViewModel @Inject constructor(
    private val repository: TaskRepository,
    settingsRepository: SettingsReader,
    private val deadlineReminderScheduler: DeadlineReminderScheduler,
    private val clock: Clock,
) : ViewModel() {

    private val _filterState = MutableStateFlow(TaskFilterState())
    private val _selectedTask = MutableStateFlow<Task?>(null)
    private val _pendingDeleteTask = MutableStateFlow<Task?>(null)
    private val _executingConflict = MutableStateFlow<ExecutingConflict?>(null)
    private val _reminderTemplates = MutableStateFlow(com.mari.app.settings.PhoneSettings.DEFAULT_DEADLINE_REMINDER_TEMPLATES)

    val reminderTemplates: StateFlow<List<DeadlineReminder>> = _reminderTemplates

    val uiState: StateFlow<AllTasksUiState> = combine(
        repository.observeTasks(),
        _filterState,
        _selectedTask,
        _pendingDeleteTask,
        _executingConflict,
    ) { tasks, filterState, selectedTask, pendingDeleteTask, executingConflict ->
        val filtered = TaskListing.filter(tasks, filterState.selectedStatuses, filterState.query)
        val sorted = TaskListing.sort(filtered, filterState.sortMode.shared)
        AllTasksUiState(sorted, filterState, selectedTask, pendingDeleteTask, executingConflict)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AllTasksUiState())

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _reminderTemplates.value = settings.deadlineReminderTemplates
            }
        }
    }

    fun onQueryChange(query: String) {
        _filterState.update { it.copy(query = query) }
    }

    fun onStatusToggle(status: TaskStatus) {
        _filterState.update { state ->
            val updated = if (status in state.selectedStatuses) state.selectedStatuses - status else state.selectedStatuses + status
            state.copy(selectedStatuses = updated)
        }
    }

    fun onSortModeChange(mode: TaskSortMode) {
        _filterState.update { it.copy(sortMode = mode) }
    }

    fun onTaskClick(task: Task) {
        _selectedTask.value = task
    }

    fun onDismissEdit() {
        _selectedTask.value = null
    }

    fun onSaveEdit(
        taskId: String,
        name: String,
        description: String,
        newStatus: TaskStatus,
        dueAt: Instant?,
        dueKind: DueKind?,
        reminders: List<DeadlineReminder>,
        colorHex: String?,
    ) {
        _selectedTask.value = null
        viewModelScope.launch {
            if (TaskValidation.findDuplicateName(repository.getTasks(), name, excludeId = taskId) != null) return@launch
            val currentTasks = repository.getTasks()
            if (newStatus == TaskStatus.EXECUTING) {
                val existing = ExecutionRules.currentlyExecuting(currentTasks)
                if (existing != null && existing.id != taskId) {
                    _executingConflict.value = ExecutingConflict(
                        existing = existing,
                        incoming = currentTasks.first { it.id == taskId }.copy(
                            name = name,
                            description = description,
                            dueAt = dueAt,
                            dueKind = dueKind,
                            deadlineReminders = reminders,
                            colorHex = colorHex,
                        ),
                    )
                    return@launch
                }
            }

            val result = repository.update { tasks ->
                tasks.map { task ->
                    if (task.id != taskId) task else ExecutionRules.applyStatusChange(
                        task = ExecutionRules.updateTaskMetadata(
                            task = task,
                            clock = clock,
                            deviceId = DeviceId.PHONE,
                            name = name,
                            description = description,
                            dueAt = dueAt,
                            dueKind = dueKind,
                            deadlineReminders = reminders,
                            colorHex = colorHex,
                        ).copy(version = task.version, updatedAt = task.updatedAt, lastModifiedBy = task.lastModifiedBy),
                        newStatus = newStatus,
                        clock = clock,
                        deviceId = DeviceId.PHONE,
                    )
                }
            }
            if (result.isSuccess) {
                deadlineReminderScheduler.cancel(taskId)
                repository.getTasks().firstOrNull { it.id == taskId }?.takeIf { it.dueAt != null }?.let(deadlineReminderScheduler::schedule)
            }
        }
    }

    fun onRequestDelete(task: Task) {
        _pendingDeleteTask.value = task
        _selectedTask.value = null
    }

    fun onDismissDelete() {
        _pendingDeleteTask.value = null
    }

    fun onConfirmDelete() {
        val task = _pendingDeleteTask.value ?: return
        _pendingDeleteTask.value = null
        viewModelScope.launch {
            repository.update { tasks ->
                tasks.map { t -> if (t.id == task.id) ExecutionRules.softDelete(t, clock, DeviceId.PHONE) else t }
            }
            deadlineReminderScheduler.cancel(task.id)
        }
    }

    fun onDismissConflict() {
        _executingConflict.value = null
    }

    fun onConflictFinish() {
        resolveConflict(TaskStatus.COMPLETED)
    }

    fun onConflictPause() {
        resolveConflict(TaskStatus.PAUSED)
    }

    private fun resolveConflict(existingStatus: TaskStatus) {
        val conflict = _executingConflict.value ?: return
        _executingConflict.value = null
        viewModelScope.launch {
            repository.update { tasks ->
                tasks.map { task ->
                    when (task.id) {
                        conflict.existing.id -> ExecutionRules.applyStatusChange(task, existingStatus, clock, DeviceId.PHONE)
                        conflict.incoming.id -> ExecutionRules.applyStatusChange(conflict.incoming.copy(version = task.version, updatedAt = task.updatedAt, lastModifiedBy = task.lastModifiedBy), TaskStatus.EXECUTING, clock, DeviceId.PHONE)
                        else -> task
                    }
                }
            }
        }
    }
}
