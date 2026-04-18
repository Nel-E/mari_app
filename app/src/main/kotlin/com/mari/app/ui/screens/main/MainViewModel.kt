package com.mari.app.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mari.app.shake.ShakeEventSource
import com.mari.app.shake.ShakeFeedback
import com.mari.app.sync.ConflictQueue
import com.mari.app.sync.PhoneSyncClient
import com.mari.shared.data.repository.TaskRepository
import com.mari.shared.data.sync.SyncConflict
import com.mari.shared.domain.Clock
import com.mari.shared.domain.DeviceId
import com.mari.shared.domain.ExecutionRules
import com.mari.shared.domain.ShakePool
import com.mari.shared.domain.Task
import com.mari.shared.domain.TaskStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MainCtaState {
    data object AddTaskOnly : MainCtaState
    data class MarkExecutingComplete(val task: Task) : MainCtaState
    data object ShakeToPick : MainCtaState
}

data class ShakeConflict(val existing: Task, val incoming: Task)

data class MainUiState(
    val ctaState: MainCtaState = MainCtaState.AddTaskOnly,
    val showExecutingSheet: Boolean = false,
    val pickedTask: Task? = null,
    val shakeConflict: ShakeConflict? = null,
    val pendingSyncConflict: SyncConflict? = null,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val clock: Clock,
    private val shakeEventSource: ShakeEventSource,
    private val shakeFeedback: ShakeFeedback,
    private val conflictQueue: ConflictQueue? = null,
    private val syncClient: PhoneSyncClient? = null,
) : ViewModel() {

    private val _showExecutingSheet = MutableStateFlow(false)
    private val _pickedTask = MutableStateFlow<Task?>(null)
    private val _shakeConflict = MutableStateFlow<ShakeConflict?>(null)
    private val _dismissedSyncConflictId = MutableStateFlow<String?>(null)

    private data class MainUiInputs(
        val tasks: List<Task>,
        val showExecutingSheet: Boolean,
        val pickedTask: Task?,
        val shakeConflict: ShakeConflict?,
        val pendingConflicts: List<SyncConflict>,
        val dismissedConflictId: String?,
    )

    val uiState: StateFlow<MainUiState> = combine(
        combine(
            repository.observeTasks(),
            _showExecutingSheet,
            _pickedTask,
            _shakeConflict,
            conflictQueue?.conflicts ?: flowOf(emptyList()),
        ) { tasks, showSheet, pickedTask, shakeConflict, pendingConflicts ->
            MainUiInputs(
                tasks = tasks,
                showExecutingSheet = showSheet,
                pickedTask = pickedTask,
                shakeConflict = shakeConflict,
                pendingConflicts = pendingConflicts,
                dismissedConflictId = null,
            )
        },
        _dismissedSyncConflictId,
    ) { inputs, dismissedConflictId ->
        MainUiState(
            ctaState = resolveCtaState(inputs.tasks),
            showExecutingSheet = inputs.showExecutingSheet,
            pickedTask = inputs.pickedTask,
            shakeConflict = inputs.shakeConflict,
            pendingSyncConflict = inputs.pendingConflicts.firstOrNull { it.local.id != dismissedConflictId },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

    init {
        viewModelScope.launch {
            shakeEventSource.shakeEvents.collect {
                val tasks = repository.getTasks()
                val candidates = ShakePool.selectCandidates(tasks)
                if (candidates.isEmpty()) return@collect
                val picked = candidates.random()
                shakeFeedback.play()
                val executing = tasks.firstOrNull {
                    it.deletedAt == null && it.status == TaskStatus.EXECUTING
                }
                if (executing != null) {
                    _shakeConflict.value = ShakeConflict(executing, picked)
                } else {
                    _pickedTask.value = picked
                }
            }
        }
    }

    fun openExecutingSheet() {
        _showExecutingSheet.value = true
    }

    fun closeExecutingSheet() {
        _showExecutingSheet.value = false
    }

    fun completeExecutingTask() {
        applyToExecuting(TaskStatus.COMPLETED)
        _showExecutingSheet.value = false
    }

    fun pauseExecutingTask() {
        applyToExecuting(TaskStatus.PAUSED)
        _showExecutingSheet.value = false
    }

    fun resetExecutingTask() {
        applyToExecuting(TaskStatus.TO_BE_DONE)
        _showExecutingSheet.value = false
    }

    fun onStartPicked() {
        val picked = _pickedTask.value ?: return
        _pickedTask.value = null
        viewModelScope.launch {
            repository.update { tasks ->
                tasks.map { task ->
                    if (task.id == picked.id) {
                        ExecutionRules.applyStatusChange(task, TaskStatus.EXECUTING, clock, DeviceId.PHONE)
                    } else task
                }
            }
        }
    }

    fun onRerollPicked() {
        val current = _pickedTask.value ?: return
        viewModelScope.launch {
            val tasks = repository.getTasks()
            val candidates = ShakePool.selectCandidates(tasks).filter { it.id != current.id }
            if (candidates.isEmpty()) return@launch
            _pickedTask.value = candidates.random()
        }
    }

    fun onDismissPicked() {
        _pickedTask.value = null
    }

    fun onDismissShakeConflict() {
        _shakeConflict.value = null
    }

    fun onShakeConflictFinish() {
        val conflict = _shakeConflict.value ?: return
        _shakeConflict.value = null
        viewModelScope.launch {
            repository.update { tasks ->
                tasks.map { task ->
                    when (task.id) {
                        conflict.existing.id -> ExecutionRules.applyStatusChange(
                            task, TaskStatus.COMPLETED, clock, DeviceId.PHONE,
                        )
                        conflict.incoming.id -> ExecutionRules.applyStatusChange(
                            task, TaskStatus.EXECUTING, clock, DeviceId.PHONE,
                        )
                        else -> task
                    }
                }
            }
        }
    }

    fun onShakeConflictPause() {
        val conflict = _shakeConflict.value ?: return
        _shakeConflict.value = null
        viewModelScope.launch {
            repository.update { tasks ->
                tasks.map { task ->
                    when (task.id) {
                        conflict.existing.id -> ExecutionRules.applyStatusChange(
                            task, TaskStatus.PAUSED, clock, DeviceId.PHONE,
                        )
                        conflict.incoming.id -> ExecutionRules.applyStatusChange(
                            task, TaskStatus.EXECUTING, clock, DeviceId.PHONE,
                        )
                        else -> task
                    }
                }
            }
        }
    }

    fun keepPhoneConflict() {
        val conflict = uiState.value.pendingSyncConflict ?: return
        viewModelScope.launch {
            _dismissedSyncConflictId.value = null
            conflictQueue?.remove(conflict.local.id)
            syncClient?.sendTasks(listOf(conflict.local))
        }
    }

    fun keepWatchConflict() {
        val conflict = uiState.value.pendingSyncConflict ?: return
        viewModelScope.launch {
            _dismissedSyncConflictId.value = null
            repository.update { tasks ->
                val merged = tasks.associateBy(Task::id).toMutableMap()
                merged[conflict.incoming.id] = conflict.incoming
                merged.values.toList()
            }
            conflictQueue?.remove(conflict.local.id)
        }
    }

    fun keepBothConflict() {
        val conflict = uiState.value.pendingSyncConflict ?: return
        viewModelScope.launch {
            _dismissedSyncConflictId.value = null
            val now = clock.nowUtc()
            repository.update { tasks ->
                tasks + conflict.incoming.copy(
                    id = UUID.randomUUID().toString(),
                    createdAt = now,
                    updatedAt = now,
                    version = 1,
                    lastModifiedBy = DeviceId.PHONE,
                )
            }
            conflictQueue?.remove(conflict.local.id)
        }
    }

    fun cancelSyncConflict() {
        _dismissedSyncConflictId.value = uiState.value.pendingSyncConflict?.local?.id
    }

    private fun applyToExecuting(newStatus: TaskStatus) {
        val executingTask = (uiState.value.ctaState as? MainCtaState.MarkExecutingComplete)?.task
            ?: return
        viewModelScope.launch {
            repository.update { tasks ->
                tasks.map { task ->
                    if (task.id == executingTask.id) {
                        ExecutionRules.applyStatusChange(task, newStatus, clock, DeviceId.PHONE)
                    } else {
                        task
                    }
                }
            }
        }
    }

    private fun resolveCtaState(tasks: List<Task>): MainCtaState {
        val active = tasks.filter { it.deletedAt == null }
        val executing = active.firstOrNull { it.status == TaskStatus.EXECUTING }
        if (executing != null) return MainCtaState.MarkExecutingComplete(executing)
        val hasPickable = active.any {
            it.status == TaskStatus.TO_BE_DONE ||
                it.status == TaskStatus.PAUSED ||
                it.status == TaskStatus.QUEUED
        }
        return if (hasPickable) MainCtaState.ShakeToPick else MainCtaState.AddTaskOnly
    }
}
