package com.mari.app.ui.screens.main

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mari.app.shake.ShakeEventSource
import com.mari.app.shake.ShakeFeedback
import com.mari.app.util.MainDispatcherRule
import com.mari.shared.domain.Clock
import com.mari.shared.domain.DeviceId
import com.mari.shared.domain.ExecutionRules
import com.mari.shared.domain.FixedClock
import com.mari.shared.domain.Task
import com.mari.shared.domain.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock: Clock = FixedClock(Instant.parse("2026-01-01T10:00:00Z"))
    private val tasksFlow = MutableStateFlow<List<Task>>(emptyList())
    private val repository = FakeMainRepository(tasksFlow)
    private val shakeSource = FakeShakeEventSource()
    private val shakeFeedback = NoOpShakeFeedback()

    private val safSource = object : com.mari.app.data.storage.SafSource {
        override val grant = kotlinx.coroutines.flow.MutableStateFlow<com.mari.app.data.storage.SafGrant>(
            com.mari.app.data.storage.SafGrant.Missing
        )
        override suspend fun init() = Unit
    }

    private fun vm() = MainViewModel(repository, clock, shakeSource, shakeFeedback, safSource)

    private fun makeTask(
        id: String,
        status: TaskStatus = TaskStatus.TO_BE_DONE,
    ): Task {
        val base = ExecutionRules.createTask(name = "Task $id", clock = clock, deviceId = DeviceId.PHONE, id = id)
        return if (status == TaskStatus.TO_BE_DONE) base
        else ExecutionRules.applyStatusChange(base, status, clock, DeviceId.PHONE)
    }

    // — CTA state —

    @Test
    fun `initial state is AddTaskOnly when no tasks`() = runTest {
        vm().uiState.test {
            assertThat(awaitItem().ctaState).isEqualTo(MainCtaState.AddTaskOnly)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `state is ShakeToPick when tasks exist but none executing`() = runTest {
        tasksFlow.value = listOf(makeTask("1"))
        vm().uiState.test {
            assertThat(awaitItem().ctaState).isEqualTo(MainCtaState.ShakeToPick)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `state is MarkExecutingComplete when a task is executing`() = runTest {
        val executingTask = makeTask("1", TaskStatus.EXECUTING)
        tasksFlow.value = listOf(executingTask)
        vm().uiState.test {
            val state = awaitItem().ctaState
            assertThat(state).isInstanceOf(MainCtaState.MarkExecutingComplete::class.java)
            assertThat((state as MainCtaState.MarkExecutingComplete).task.id).isEqualTo("1")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `AddTaskOnly when only terminal tasks exist`() = runTest {
        tasksFlow.value = listOf(
            makeTask("1", TaskStatus.COMPLETED),
            makeTask("2", TaskStatus.DISCARDED),
        )
        vm().uiState.test {
            assertThat(awaitItem().ctaState).isEqualTo(MainCtaState.AddTaskOnly)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // — Executing sheet actions —

    @Test
    fun `completeExecutingTask transitions task to COMPLETED`() = runTest {
        val executingTask = makeTask("1", TaskStatus.EXECUTING)
        tasksFlow.value = listOf(executingTask)
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            viewModel.completeExecutingTask()
            val updated = tasksFlow.value.first { it.id == "1" }
            assertThat(updated.status).isEqualTo(TaskStatus.COMPLETED)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `pauseExecutingTask transitions task to PAUSED`() = runTest {
        val executingTask = makeTask("1", TaskStatus.EXECUTING)
        tasksFlow.value = listOf(executingTask)
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            viewModel.pauseExecutingTask()
            val updated = tasksFlow.value.first { it.id == "1" }
            assertThat(updated.status).isEqualTo(TaskStatus.PAUSED)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resetExecutingTask transitions task to TO_BE_DONE`() = runTest {
        val executingTask = makeTask("1", TaskStatus.EXECUTING)
        tasksFlow.value = listOf(executingTask)
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            viewModel.resetExecutingTask()
            val updated = tasksFlow.value.first { it.id == "1" }
            assertThat(updated.status).isEqualTo(TaskStatus.TO_BE_DONE)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `openExecutingSheet and closeExecutingSheet toggle showExecutingSheet`() = runTest {
        tasksFlow.value = listOf(makeTask("1", TaskStatus.EXECUTING))
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()

            viewModel.openExecutingSheet()
            assertThat(awaitItem().showExecutingSheet).isTrue()

            viewModel.closeExecutingSheet()
            assertThat(awaitItem().showExecutingSheet).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // — Shake: pick dialog —

    @Test
    fun `shake with one pickable task shows that task in pickedTask`() = runTest {
        tasksFlow.value = listOf(makeTask("1"))
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem() // initial
            shakeSource.emit()
            val state = awaitItem()
            assertThat(state.pickedTask).isNotNull()
            assertThat(state.pickedTask?.id).isEqualTo("1")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shake with no eligible tasks does not show picked dialog`() = runTest {
        tasksFlow.value = listOf(makeTask("1", TaskStatus.COMPLETED))
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            shakeSource.emit()
            // No new emission expected — pickedTask remains null
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onDismissPicked clears pickedTask`() = runTest {
        tasksFlow.value = listOf(makeTask("1"))
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            shakeSource.emit()
            awaitItem() // pickedTask set
            viewModel.onDismissPicked()
            assertThat(awaitItem().pickedTask).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onStartPicked starts the picked task and clears dialog`() = runTest {
        tasksFlow.value = listOf(makeTask("1"))
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            shakeSource.emit()
            awaitItem() // pickedTask set
            viewModel.onStartPicked()
            val state = awaitItem()
            assertThat(state.pickedTask).isNull()
            cancelAndIgnoreRemainingEvents()
        }
        val started = tasksFlow.value.first { it.id == "1" }
        assertThat(started.status).isEqualTo(TaskStatus.EXECUTING)
    }

    @Test
    fun `onRerollPicked selects a different task`() = runTest {
        tasksFlow.value = listOf(makeTask("1"), makeTask("2"))
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            shakeSource.emit()
            val afterShake = awaitItem()
            val firstPick = afterShake.pickedTask
            assertThat(firstPick).isNotNull()
            viewModel.onRerollPicked()
            val afterReroll = awaitItem()
            assertThat(afterReroll.pickedTask?.id).isNotEqualTo(firstPick?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // — Shake: conflict dialog —

    @Test
    fun `shake when a task is executing shows conflict dialog`() = runTest {
        val executing = makeTask("1", TaskStatus.EXECUTING)
        val pickable = makeTask("2")
        tasksFlow.value = listOf(executing, pickable)
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            shakeSource.emit()
            val state = awaitItem()
            assertThat(state.shakeConflict).isNotNull()
            assertThat(state.shakeConflict?.existing?.id).isEqualTo("1")
            assertThat(state.shakeConflict?.incoming?.id).isEqualTo("2")
            assertThat(state.pickedTask).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onDismissShakeConflict clears conflict`() = runTest {
        val executing = makeTask("1", TaskStatus.EXECUTING)
        val pickable = makeTask("2")
        tasksFlow.value = listOf(executing, pickable)
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            shakeSource.emit()
            awaitItem() // conflict shown
            viewModel.onDismissShakeConflict()
            assertThat(awaitItem().shakeConflict).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onShakeConflictFinish completes existing and starts incoming`() = runTest {
        val executing = makeTask("1", TaskStatus.EXECUTING)
        val pickable = makeTask("2")
        tasksFlow.value = listOf(executing, pickable)
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            shakeSource.emit()
            awaitItem() // conflict shown
            viewModel.onShakeConflictFinish()
            assertThat(awaitItem().shakeConflict).isNull()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(tasksFlow.value.first { it.id == "1" }.status).isEqualTo(TaskStatus.COMPLETED)
        assertThat(tasksFlow.value.first { it.id == "2" }.status).isEqualTo(TaskStatus.EXECUTING)
    }

    @Test
    fun `onShakeConflictPause pauses existing and starts incoming`() = runTest {
        val executing = makeTask("1", TaskStatus.EXECUTING)
        val pickable = makeTask("2")
        tasksFlow.value = listOf(executing, pickable)
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            shakeSource.emit()
            awaitItem() // conflict shown
            viewModel.onShakeConflictPause()
            assertThat(awaitItem().shakeConflict).isNull()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(tasksFlow.value.first { it.id == "1" }.status).isEqualTo(TaskStatus.PAUSED)
        assertThat(tasksFlow.value.first { it.id == "2" }.status).isEqualTo(TaskStatus.EXECUTING)
    }
}

private class FakeShakeEventSource : ShakeEventSource {
    private val _shakeEvents = MutableSharedFlow<Unit>()
    override val shakeEvents: SharedFlow<Unit> = _shakeEvents
    suspend fun emit() = _shakeEvents.emit(Unit)
}

private class NoOpShakeFeedback : ShakeFeedback {
    override fun play() = Unit
}

private class FakeMainRepository(
    private val flow: MutableStateFlow<List<Task>>,
) : com.mari.shared.data.repository.TaskRepository {

    override fun observeTasks(): Flow<List<Task>> = flow
    override suspend fun getTasks(): List<Task> = flow.value
    override suspend fun update(transform: (List<Task>) -> List<Task>): Result<Unit> {
        flow.value = transform(flow.value)
        return Result.success(Unit)
    }
    override suspend fun delete(taskId: String): Result<Unit> {
        flow.value = flow.value.filterNot { it.id == taskId }
        return Result.success(Unit)
    }
}
