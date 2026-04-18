package com.mari.wear.ui.screens.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mari.shared.data.repository.TaskRepository
import com.mari.shared.domain.Clock
import com.mari.shared.domain.DeviceId
import com.mari.shared.domain.ExecutionRules
import com.mari.shared.domain.Task
import com.mari.shared.domain.TaskStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WearAllTasksUiState(
    val tasks: List<Task> = emptyList(),
    val selectedTask: Task? = null,
)

@HiltViewModel
class AllTasksViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _selectedTask = MutableStateFlow<Task?>(null)

    val uiState: StateFlow<WearAllTasksUiState> = combine(
        repository.observeTasks(),
        _selectedTask,
    ) { tasks, selected ->
        WearAllTasksUiState(
            tasks = tasks.filter { it.deletedAt == null },
            selectedTask = selected,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WearAllTasksUiState(),
    )

    fun onTaskClick(task: Task) {
        _selectedTask.value = task
    }

    fun onDismissActions() {
        _selectedTask.value = null
    }

    fun onSetExecuting(task: Task) {
        _selectedTask.value = null
        viewModelScope.launch {
            repository.update { tasks ->
                tasks.map {
                    if (it.id == task.id) {
                        ExecutionRules.applyStatusChange(it, TaskStatus.EXECUTING, clock, DeviceId.WATCH)
                    } else it
                }
            }
        }
    }

    fun onComplete(task: Task) {
        _selectedTask.value = null
        viewModelScope.launch {
            repository.update { tasks ->
                tasks.map {
                    if (it.id == task.id) {
                        ExecutionRules.applyStatusChange(it, TaskStatus.COMPLETED, clock, DeviceId.WATCH)
                    } else it
                }
            }
        }
    }

    fun onDelete(task: Task) {
        _selectedTask.value = null
        viewModelScope.launch {
            repository.update { tasks ->
                tasks.map {
                    if (it.id == task.id) ExecutionRules.softDelete(it, clock, DeviceId.WATCH) else it
                }
            }
        }
    }
}
