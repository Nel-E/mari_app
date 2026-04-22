package com.mari.app.ui.screens.add

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mari.app.reminders.DeadlineReminderScheduler
import com.mari.app.settings.PhoneSettings
import com.mari.app.settings.SettingsReader
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
    private val settings = FakeSettingsReader()
    private val scheduler = NoopScheduler()

    private fun vm() = AddTaskViewModel(repository, settings, scheduler, clock)

    @Test
    fun `blank name sets error and does not save`() = runTest {
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            viewModel.onNameChange("   ")
            awaitItem()
            viewModel.save()
            val state = awaitItem()
            assertThat(state.nameError).isNotNull()
            assertThat(state.saved).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(tasksFlow.value).isEmpty()
    }

    @Test
    fun `valid name saves task and sets saved`() = runTest {
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            viewModel.onNameChange("Buy groceries")
            awaitItem()
            viewModel.save()
            val firstAfterSave = awaitItem()
            val final = if (firstAfterSave.saved) firstAfterSave else awaitItem()
            assertThat(final.saved).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(tasksFlow.value).hasSize(1)
        assertThat(tasksFlow.value.first().name).isEqualTo("Buy groceries")
    }

    @Test
    fun `onNameChange clears previous error`() = runTest {
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            viewModel.save()
            val withError = awaitItem()
            assertThat(withError.nameError).isNotNull()
            viewModel.onNameChange("Valid")
            val cleared = awaitItem()
            assertThat(cleared.nameError).isNull()
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

private class FakeSettingsReader : SettingsReader {
    private val _settings = MutableStateFlow(PhoneSettings())
    override val settings: Flow<PhoneSettings> = _settings
    override suspend fun current(): PhoneSettings = _settings.value
}

private class NoopScheduler : DeadlineReminderScheduler {
    override fun schedule(task: Task) = Unit
    override fun cancel(taskId: String) = Unit
}
