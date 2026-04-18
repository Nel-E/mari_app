package com.mari.wear.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mari.shared.data.repository.TaskRepository
import com.mari.shared.domain.Clock
import com.mari.shared.domain.DeviceId
import com.mari.shared.domain.ExecutionRules
import com.mari.shared.domain.ShakePool
import com.mari.shared.domain.Task
import com.mari.shared.domain.TaskStatus
import com.mari.wear.shake.ShakeEventSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface WearCtaState {
    data object AddTaskOnly : WearCtaState
    data class MarkExecutingComplete(val task: Task) : WearCtaState
    data object ShakeToPick : WearCtaState
}

data class WearPickedConflict(val existing: Task, val incoming: Task)

data class WearMainUiState(
    val ctaState: WearCtaState = WearCtaState.AddTaskOnly,
    val pickedTask: Task? = null,
    val conflict: WearPickedConflict? = null,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val clock: Clock,
    private val shakeEventSource: ShakeEventSource,
) : ViewModel() {

    private val _pickedTask = MutableStateFlow<Task?>(null)
    private val _conflict = MutableStateFlow<WearPickedConflict?>(null)

    val uiState: StateFlow<WearMainUiState> = combine(
        repository.observeTasks(),
        _pickedTask,
        _conflict,
    ) { tasks, picked, conflict ->
        WearMainUiState(
            ctaState = resolveCtaState(tasks),
            pickedTask = picked,
            conflict = conflict,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WearMainUiState(),
    )

    init {
        viewModelScope.launch {
            shakeEventSource.shakeEvents.collect {
                val tasks = repository.getTasks()
                val candidates = ShakePool.selectCandidates(tasks)
                if (candidates.isEmpty()) return@collect
                val picked = candidates.random()
                val executing = tasks.firstOrNull {
                    it.deletedAt == null && it.status == TaskStatus.EXECUTING
                }
                if (executing != null) {
                    _conflict.value = WearPickedConflict(executing, picked)
                } else {
                    _pickedTask.value = picked
                }
            }
        }
    }

    fun completeExecutingTask() {
        applyToExecuting(TaskStatus.COMPLETED)
    }

    fun pauseExecutingTask() {
        applyToExecuting(TaskStatus.PAUSED)
    }

    fun onStartPicked() {
        val picked = _pickedTask.value ?: return
        _pickedTask.value = null
        viewModelScope.launch {
            repository.update { tasks ->
                tasks.map { task ->
                    if (task.id == picked.id) {
                        ExecutionRules.applyStatusChange(task, TaskStatus.EXECUTING, clock, DeviceId.WATCH)
                    } else task
                }
            }
        }
    }

    fun onDismissPicked() {
        _pickedTask.value = null
    }

    fun onConflictFinish() {
        val conflict = _conflict.value ?: return
        _conflict.value = null
        viewModelScope.launch {
            repository.update { tasks ->
                tasks.map { task ->
                    when (task.id) {
                        conflict.existing.id -> ExecutionRules.applyStatusChange(task, TaskStatus.COMPLETED, clock, DeviceId.WATCH)
                        conflict.incoming.id -> ExecutionRules.applyStatusChange(task, TaskStatus.EXECUTING, clock, DeviceId.WATCH)
                        else -> task
                    }
                }
            }
        }
    }

    fun onConflictPause() {
        val conflict = _conflict.value ?: return
        _conflict.value = null
        viewModelScope.launch {
            repository.update { tasks ->
                tasks.map { task ->
                    when (task.id) {
                        conflict.existing.id -> ExecutionRules.applyStatusChange(task, TaskStatus.PAUSED, clock, DeviceId.WATCH)
                        conflict.incoming.id -> ExecutionRules.applyStatusChange(task, TaskStatus.EXECUTING, clock, DeviceId.WATCH)
                        else -> task
                    }
                }
            }
        }
    }

    fun onDismissConflict() {
        _conflict.value = null
    }

    private fun applyToExecuting(newStatus: TaskStatus) {
        val executingTask = (uiState.value.ctaState as? WearCtaState.MarkExecutingComplete)?.task
            ?: return
        viewModelScope.launch {
            repository.update { tasks ->
                tasks.map { task ->
                    if (task.id == executingTask.id) {
                        ExecutionRules.applyStatusChange(task, newStatus, clock, DeviceId.WATCH)
                    } else task
                }
            }
        }
    }

    private fun resolveCtaState(tasks: List<Task>): WearCtaState {
        val active = tasks.filter { it.deletedAt == null }
        val executing = active.firstOrNull { it.status == TaskStatus.EXECUTING }
        if (executing != null) return WearCtaState.MarkExecutingComplete(executing)
        val hasPickable = active.any {
            it.status == TaskStatus.TO_BE_DONE ||
                it.status == TaskStatus.PAUSED ||
                it.status == TaskStatus.QUEUED
        }
        return if (hasPickable) WearCtaState.ShakeToPick else WearCtaState.AddTaskOnly
    }
}
