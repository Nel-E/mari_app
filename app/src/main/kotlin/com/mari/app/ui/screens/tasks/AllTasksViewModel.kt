package com.mari.app.ui.screens.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mari.app.reminders.DeadlineReminderScheduler
import com.mari.app.settings.SettingsReader
import com.mari.shared.data.repository.TaskRepository
import com.mari.shared.domain.Clock
import com.mari.shared.domain.DeviceId
import com.mari.shared.domain.ExecutionRules
import com.mari.shared.domain.Task
import com.mari.shared.domain.TaskColor
import com.mari.shared.domain.TaskListing
import com.mari.shared.domain.TaskPriority
import com.mari.shared.domain.TaskStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AllTasksUiState(
    val tasks: List<Task> = emptyList(),
    val filterState: TaskFilterState = TaskFilterState(),
    val priorityColors: Map<TaskPriority, String?> = com.mari.app.settings.PhoneSettings.DEFAULT_PRIORITY_COLORS,
)

@HiltViewModel
class AllTasksViewModel @Inject constructor(
    private val repository: TaskRepository,
    settingsRepository: SettingsReader,
    private val deadlineReminderScheduler: DeadlineReminderScheduler,
    private val clock: Clock,
) : ViewModel() {

    private val _filterState = MutableStateFlow(TaskFilterState())
    private val _priorityColors = MutableStateFlow(com.mari.app.settings.PhoneSettings.DEFAULT_PRIORITY_COLORS)
    private val _navigationEvents = Channel<String>(Channel.BUFFERED)
    val navigationEvents = _navigationEvents.receiveAsFlow()

    val uiState: StateFlow<AllTasksUiState> = combine(
        repository.observeTasks(),
        _filterState,
        _priorityColors,
    ) { tasks, filterState, priorityColors ->
        val filtered = TaskListing.filter(tasks, filterState.selectedStatuses, filterState.query)
        val sorted = TaskListing.sort(filtered, filterState.sortMode.shared)
        AllTasksUiState(sorted, filterState, priorityColors)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AllTasksUiState())

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _priorityColors.value = settings.priorityColors
            }
        }
    }

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
        viewModelScope.launch { _navigationEvents.send(task.id) }
    }

    fun onQuickStatusChange(taskId: String, newStatus: TaskStatus) {
        viewModelScope.launch {
            repository.update { tasks ->
                tasks.map { task ->
                    if (task.id != taskId) task
                    else ExecutionRules.applyStatusChange(task, newStatus, clock, DeviceId.PHONE)
                }
            }
        }
    }

    fun onQuickPriorityChange(taskId: String, newPriority: TaskPriority) {
        viewModelScope.launch {
            val priorityColors = _priorityColors.value
            repository.update { tasks ->
                tasks.map { task ->
                    if (task.id != taskId) task
                    else {
                        val newColor = if (!task.useCustomColor) {
                            priorityColors[newPriority]?.let { TaskColor.parse(it).getOrNull()?.hex }
                        } else {
                            task.colorHex
                        }
                        ExecutionRules.updateTaskMetadata(
                            task = task,
                            clock = clock,
                            deviceId = DeviceId.PHONE,
                            name = task.name,
                            priority = newPriority,
                            colorHex = newColor,
                        )
                    }
                }
            }
        }
    }
}

