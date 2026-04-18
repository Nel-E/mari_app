package com.mari.app.ui.screens.main

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mari.app.shake.ShakeEventSource
import com.mari.app.shake.ShakeFeedback
import com.mari.app.util.MainDispatcherRule
import com.mari.shared.data.repository.TaskRepository
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

/**
 * End-to-end shake flow: sensor event → task pick → start → EXECUTING status persisted.
 * Uses fakes for repository and shake source; no Android instrumentation required.
 */
class ShakeE2ETest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock: Clock = FixedClock(Instant.parse("2026-04-18T10:00:00Z"))
    private val tasksFlow = MutableStateFlow<List<Task>>(emptyList())
    private val repository = FakeE2ERepository(tasksFlow)
    private val shakeSource = FakeE2EShakeSource()
    private val shakeFeedback = NoOpE2EShakeFeedback()

    private fun vm() = MainViewModel(repository, clock, shakeSource, shakeFeedback)

    private fun task(id: String, status: TaskStatus = TaskStatus.TO_BE_DONE): Task {
        val base = ExecutionRules.createTask("Task $id", clock, DeviceId.PHONE, id)
        return if (status == TaskStatus.TO_BE_DONE) base
        else ExecutionRules.applyStatusChange(base, status, clock, DeviceId.PHONE)
    }

    @Test
    fun `shake picks a task then onStartPicked transitions it to EXECUTING`() = runTest {
        tasksFlow.value = listOf(task("t1"))
        val viewModel = vm()

        viewModel.uiState.test {
            awaitItem() // initial

            shakeSource.emit()
            val afterShake = awaitItem()
            assertThat(afterShake.pickedTask).isNotNull()
            assertThat(afterShake.pickedTask?.id).isEqualTo("t1")

            viewModel.onStartPicked()
            val afterStart = awaitItem()
            assertThat(afterStart.pickedTask).isNull()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(tasksFlow.value.first { it.id == "t1" }.status)
            .isEqualTo(TaskStatus.EXECUTING)
    }

    @Test
    fun `shake → conflict dialog when task already executing → finish switches tasks`() = runTest {
        val executing = task("e1", TaskStatus.EXECUTING)
        val pickable = task("p1")
        tasksFlow.value = listOf(executing, pickable)
        val viewModel = vm()

        viewModel.uiState.test {
            awaitItem()

            shakeSource.emit()
            val conflictState = awaitItem()
            assertThat(conflictState.shakeConflict).isNotNull()
            assertThat(conflictState.shakeConflict?.existing?.id).isEqualTo("e1")
            assertThat(conflictState.shakeConflict?.incoming?.id).isEqualTo("p1")

            viewModel.onShakeConflictFinish()
            val afterFinish = awaitItem()
            assertThat(afterFinish.shakeConflict).isNull()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(tasksFlow.value.first { it.id == "e1" }.status).isEqualTo(TaskStatus.COMPLETED)
        assertThat(tasksFlow.value.first { it.id == "p1" }.status).isEqualTo(TaskStatus.EXECUTING)
    }

    @Test
    fun `shake → conflict dialog when task already executing → pause suspends and starts new`() =
        runTest {
            val executing = task("e1", TaskStatus.EXECUTING)
            val pickable = task("p1")
            tasksFlow.value = listOf(executing, pickable)
            val viewModel = vm()

            viewModel.uiState.test {
                awaitItem()

                shakeSource.emit()
                awaitItem() // conflict shown

                viewModel.onShakeConflictPause()
                awaitItem() // conflict cleared
                cancelAndIgnoreRemainingEvents()
            }

            assertThat(tasksFlow.value.first { it.id == "e1" }.status).isEqualTo(TaskStatus.PAUSED)
            assertThat(tasksFlow.value.first { it.id == "p1" }.status).isEqualTo(TaskStatus.EXECUTING)
        }

    @Test
    fun `shake with only completed tasks produces no pick dialog`() = runTest {
        tasksFlow.value = listOf(task("done", TaskStatus.COMPLETED))
        val viewModel = vm()

        viewModel.uiState.test {
            awaitItem()
            shakeSource.emit()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onDismissPicked clears the pick dialog without starting`() = runTest {
        tasksFlow.value = listOf(task("t1"))
        val viewModel = vm()

        viewModel.uiState.test {
            awaitItem()
            shakeSource.emit()
            awaitItem() // pick shown

            viewModel.onDismissPicked()
            assertThat(awaitItem().pickedTask).isNull()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(tasksFlow.value.first { it.id == "t1" }.status).isEqualTo(TaskStatus.TO_BE_DONE)
    }

    @Test
    fun `onRerollPicked selects a different task from the pool`() = runTest {
        tasksFlow.value = listOf(task("t1"), task("t2"), task("t3"))
        val viewModel = vm()

        viewModel.uiState.test {
            awaitItem()
            shakeSource.emit()
            val afterShake = awaitItem()
            val firstPick = requireNotNull(afterShake.pickedTask)

            viewModel.onRerollPicked()
            val afterReroll = awaitItem()
            assertThat(afterReroll.pickedTask?.id).isNotEqualTo(firstPick.id)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

private class FakeE2EShakeSource : ShakeEventSource {
    private val _shakeEvents = MutableSharedFlow<Unit>()
    override val shakeEvents: SharedFlow<Unit> = _shakeEvents
    suspend fun emit() = _shakeEvents.emit(Unit)
}

private class NoOpE2EShakeFeedback : ShakeFeedback {
    override fun play() = Unit
}

private class FakeE2ERepository(
    private val flow: MutableStateFlow<List<Task>>,
) : TaskRepository {
    override fun observeTasks(): Flow<List<Task>> = flow
    override suspend fun getTasks(): List<Task> = flow.value
    override suspend fun update(transform: (List<Task>) -> List<Task>): Result<Unit> {
        flow.value = transform(flow.value)
        return Result.success(Unit)
    }
}
