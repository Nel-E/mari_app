package com.mari.app.ui.screens.tasks

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mari.app.util.MainDispatcherRule
import com.mari.shared.domain.DeviceId
import com.mari.shared.domain.ExecutionRules
import com.mari.shared.domain.FixedClock
import com.mari.shared.domain.Task
import com.mari.shared.domain.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class AllTasksViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FixedClock(Instant.parse("2026-01-01T10:00:00Z"))
    private val tasksFlow = MutableStateFlow<List<Task>>(emptyList())
    private val repository = FakeAllTasksRepo(tasksFlow)

    private fun vm() = AllTasksViewModel(repository, clock)

    private fun makeTask(
        id: String,
        description: String = "Task $id",
        status: TaskStatus = TaskStatus.TO_BE_DONE,
    ): Task {
        val base = ExecutionRules.createTask(description, clock, DeviceId.PHONE, id)
        return if (status == TaskStatus.TO_BE_DONE) base
        else ExecutionRules.applyStatusChange(base, status, clock, DeviceId.PHONE)
    }

    @Test
    fun `empty state when no tasks`() = runTest {
        vm().uiState.test {
            assertThat(awaitItem().tasks).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tasks are sorted executing first by default`() = runTest {
        val t1 = makeTask("1", status = TaskStatus.PAUSED)
        val t2 = makeTask("2", status = TaskStatus.EXECUTING)
        tasksFlow.value = listOf(t1, t2)
        vm().uiState.test {
            assertThat(awaitItem().tasks.first().id).isEqualTo("2")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `soft-deleted tasks are hidden`() = runTest {
        val active = makeTask("1")
        val deleted = ExecutionRules.softDelete(makeTask("2"), clock, DeviceId.PHONE)
        tasksFlow.value = listOf(active, deleted)
        vm().uiState.test {
            assertThat(awaitItem().tasks).hasSize(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onQueryChange filters tasks by description`() = runTest {
        tasksFlow.value = listOf(
            makeTask("1", description = "Buy milk"),
            makeTask("2", description = "Read book"),
        )
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem() // initial
            viewModel.onQueryChange("milk")
            val filtered = awaitItem()
            assertThat(filtered.tasks).hasSize(1)
            assertThat(filtered.tasks.first().id).isEqualTo("1")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onStatusToggle includes only selected statuses`() = runTest {
        tasksFlow.value = listOf(
            makeTask("1", status = TaskStatus.TO_BE_DONE),
            makeTask("2", status = TaskStatus.PAUSED),
            makeTask("3", status = TaskStatus.EXECUTING),
        )
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            viewModel.onStatusToggle(TaskStatus.PAUSED)
            val result = awaitItem()
            assertThat(result.tasks).hasSize(1)
            assertThat(result.tasks.first().id).isEqualTo("2")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onStatusToggle deselects already selected status`() = runTest {
        tasksFlow.value = listOf(makeTask("1", status = TaskStatus.TO_BE_DONE))
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            viewModel.onStatusToggle(TaskStatus.TO_BE_DONE)
            val withFilter = awaitItem()
            assertThat(withFilter.filterState.selectedStatuses).contains(TaskStatus.TO_BE_DONE)

            viewModel.onStatusToggle(TaskStatus.TO_BE_DONE)
            val cleared = awaitItem()
            assertThat(cleared.filterState.selectedStatuses).doesNotContain(TaskStatus.TO_BE_DONE)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onSortModeChange updates filterState`() = runTest {
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            viewModel.onSortModeChange(TaskSortMode.A_Z)
            assertThat(awaitItem().filterState.sortMode).isEqualTo(TaskSortMode.A_Z)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onTaskClick sets selectedTask`() = runTest {
        val task = makeTask("1")
        tasksFlow.value = listOf(task)
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            viewModel.onTaskClick(task)
            assertThat(awaitItem().selectedTask).isEqualTo(task)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onDismissEdit clears selectedTask`() = runTest {
        val task = makeTask("1")
        tasksFlow.value = listOf(task)
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            viewModel.onTaskClick(task)
            awaitItem()
            viewModel.onDismissEdit()
            assertThat(awaitItem().selectedTask).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onSaveEdit updates task in repository and closes sheet`() = runTest {
        val task = makeTask("1")
        tasksFlow.value = listOf(task)
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            viewModel.onTaskClick(task)
            awaitItem() // selectedTask set
            viewModel.onSaveEdit("1", "Updated desc", TaskStatus.PAUSED)
            val afterSave = awaitItem() // selectedTask cleared
            assertThat(afterSave.selectedTask).isNull()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repository.tasks.first { it.id == "1" }.description).isEqualTo("Updated desc")
        assertThat(repository.tasks.first { it.id == "1" }.status).isEqualTo(TaskStatus.PAUSED)
    }

    @Test
    fun `onRequestDelete sets pendingDeleteTask and closes edit sheet`() = runTest {
        val task = makeTask("1")
        tasksFlow.value = listOf(task)
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            viewModel.onRequestDelete(task)
            val state = awaitItem()
            assertThat(state.pendingDeleteTask).isEqualTo(task)
            assertThat(state.selectedTask).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onConfirmDelete soft-deletes task`() = runTest {
        val task = makeTask("1")
        tasksFlow.value = listOf(task)
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            viewModel.onRequestDelete(task)
            awaitItem()
            viewModel.onConfirmDelete()
            val firstAfterDelete = awaitItem()
            val afterDelete = if (firstAfterDelete.tasks.isEmpty()) firstAfterDelete else awaitItem()
            assertThat(afterDelete.pendingDeleteTask).isNull()
            assertThat(afterDelete.tasks).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onDismissDelete clears pendingDeleteTask`() = runTest {
        val task = makeTask("1")
        tasksFlow.value = listOf(task)
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            viewModel.onRequestDelete(task)
            awaitItem()
            viewModel.onDismissDelete()
            assertThat(awaitItem().pendingDeleteTask).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }
}

private class FakeAllTasksRepo(private val flow: MutableStateFlow<List<Task>>) :
    com.mari.shared.data.repository.TaskRepository {

    val tasks: List<Task> get() = flow.value

    override fun observeTasks(): Flow<List<Task>> = flow
    override suspend fun getTasks(): List<Task> = flow.value
    override suspend fun update(transform: (List<Task>) -> List<Task>): Result<Unit> {
        flow.value = transform(flow.value)
        return Result.success(Unit)
    }
}
