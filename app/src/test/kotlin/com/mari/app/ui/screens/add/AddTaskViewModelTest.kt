package com.mari.app.ui.screens.add

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mari.app.util.MainDispatcherRule
import com.mari.shared.domain.FixedClock
import com.mari.shared.domain.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class AddTaskViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FixedClock(Instant.parse("2026-01-01T10:00:00Z"))
    private val tasksFlow = MutableStateFlow<List<Task>>(emptyList())
    private val repository = FakeRepo(tasksFlow)

    private fun vm() = AddTaskViewModel(repository, clock)

    @Test
    fun `blank description sets error and does not save`() = runTest {
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            viewModel.onDescriptionChange("   ")
            awaitItem()
            viewModel.save()
            val state = awaitItem()
            assertThat(state.descriptionError).isNotNull()
            assertThat(state.saved).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(tasksFlow.value).isEmpty()
    }

    @Test
    fun `valid description saves task and sets saved`() = runTest {
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            viewModel.onDescriptionChange("Buy groceries")
            awaitItem()
            viewModel.save()
            val firstAfterSave = awaitItem()
            val final = if (firstAfterSave.saved) firstAfterSave else awaitItem()
            assertThat(final.saved).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(tasksFlow.value).hasSize(1)
        assertThat(tasksFlow.value.first().description).isEqualTo("Buy groceries")
    }

    @Test
    fun `onDescriptionChange clears previous error`() = runTest {
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            viewModel.save() // triggers error on empty description
            val withError = awaitItem()
            assertThat(withError.descriptionError).isNotNull()
            viewModel.onDescriptionChange("Valid")
            val cleared = awaitItem()
            assertThat(cleared.descriptionError).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `description over 500 chars sets error`() = runTest {
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            viewModel.onDescriptionChange("a".repeat(501))
            awaitItem()
            viewModel.save()
            assertThat(awaitItem().descriptionError).isNotNull()
            cancelAndIgnoreRemainingEvents()
        }
    }
}

private class FakeRepo(private val flow: MutableStateFlow<List<Task>>) :
    com.mari.shared.data.repository.TaskRepository {
    override fun observeTasks(): Flow<List<Task>> = flow
    override suspend fun getTasks(): List<Task> = flow.value
    override suspend fun update(transform: (List<Task>) -> List<Task>): Result<Unit> {
        flow.value = transform(flow.value)
        return Result.success(Unit)
    }
}
