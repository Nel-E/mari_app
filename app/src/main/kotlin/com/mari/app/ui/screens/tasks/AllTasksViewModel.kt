package com.mari.app.ui.screens.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mari.shared.data.repository.TaskRepository
import com.mari.shared.domain.Clock
import com.mari.shared.domain.DeviceId
import com.mari.shared.domain.ExecutionRules
import com.mari.shared.domain.Task
import com.mari.shared.domain.TaskListing
import com.mari.shared.domain.TaskStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    private val clock: Clock,
) : ViewModel() {

    private val _filterState = MutableStateFlow(TaskFilterState())
    private val _selectedTask = MutableStateFlow<Task?>(null)
    private val _pendingDeleteTask = MutableStateFlow<Task?>(null)
    private val _executingConflict = MutableStateFlow<ExecutingConflict?>(null)

    val uiState: StateFlow<AllTasksUiState> = combine(
        repository.observeTasks(),
        _filterState,
        _selectedTask,
        _pendingDeleteTask,
        _executingConflict,
    ) { tasks, filterState, selectedTask, pendingDeleteTask, executingConflict ->
        val filtered = TaskListing.filter(
            tasks = tasks,
            selectedStatuses = filterState.selectedStatuses,
            query = filterState.query,
        )
        val sorted = TaskListing.sort(filtered, filterState.sortMode.shared)
        AllTasksUiState(
            tasks = sorted,
            filterState = filterState,
            selectedTask = selectedTask,
            pendingDeleteTask = pendingDeleteTask,
            executingConflict = executingConflict,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AllTasksUiState(),
    )

    fun onQueryChange(query: String) {
        _filterState.update { it.copy(query = query) }
    }

    fun onStatusToggle(status: TaskStatus) {
        _filterState.update { state ->
            val updated = if (status in state.selectedStatuses) {
                state.selectedStatuses - status
            } else {
                state.selectedStatuses + status
            }
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

    fun onSaveEdit(taskId: String, description: String, newStatus: TaskStatus) {
        _selectedTask.value = null
        viewModelScope.launch {
            if (newStatus == TaskStatus.EXECUTING) {
                val currentTasks = repository.getTasks()
                val existing = ExecutionRules.currentlyExecuting(currentTasks)
                if (existing != null && existing.id != taskId) {
                    repository.update { tasks ->
                        tasks.map { task ->
                            if (task.id == taskId) {
                                ExecutionRules.applyStatusChange(
                                    task = task.copy(description = description),
                                    newStatus = task.status,
                                    clock = clock,
                                    deviceId = DeviceId.PHONE,
                                )
                            } else task
                        }
                    }
                    val snapshot = currentTasks.first { it.id == taskId }.copy(description = description)
                    _executingConflict.value = ExecutingConflict(existing = existing, incoming = snapshot)
                    return@launch
                }
            }
            repository.update { tasks ->
                tasks.map { task ->
                    if (task.id != taskId) return@map task
                    ExecutionRules.applyStatusChange(
                        task = task.copy(description = description),
                        newStatus = newStatus,
                        clock = clock,
                        deviceId = DeviceId.PHONE,
                    )
                }
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
                tasks.map { t ->
                    if (t.id == task.id) ExecutionRules.softDelete(t, clock, DeviceId.PHONE) else t
                }
            }
        }
    }

    fun onDismissConflict() {
        _executingConflict.value = null
    }

    fun onConflictFinish() {
        val conflict = _executingConflict.value ?: return
        _executingConflict.value = null
        viewModelScope.launch {
            repository.update { tasks ->
                tasks.map { task ->
                    when (task.id) {
                        conflict.existing.id -> ExecutionRules.applyStatusChange(task, TaskStatus.COMPLETED, clock, DeviceId.PHONE)
                        conflict.incoming.id -> ExecutionRules.applyStatusChange(task, TaskStatus.EXECUTING, clock, DeviceId.PHONE)
                        else -> task
                    }
                }
            }
        }
    }

    fun onConflictPause() {
        val conflict = _executingConflict.value ?: return
        _executingConflict.value = null
        viewModelScope.launch {
            repository.update { tasks ->
                tasks.map { task ->
                    when (task.id) {
                        conflict.existing.id -> ExecutionRules.applyStatusChange(task, TaskStatus.PAUSED, clock, DeviceId.PHONE)
                        conflict.incoming.id -> ExecutionRules.applyStatusChange(task, TaskStatus.EXECUTING, clock, DeviceId.PHONE)
                        else -> task
                    }
                }
            }
        }
    }
}
