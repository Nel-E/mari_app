package com.mari.app.ui.screens.add

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mari.app.util.MainDispatcherRule
import com.mari.app.voice.VoiceResult
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

    // — Manual text input —

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
            val final = awaitItem()
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

    // — Voice input —

    @Test
    fun `voice success fills description and clears retry`() = runTest {
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            viewModel.onVoiceResult(VoiceResult.Success("Buy milk"))
            val state = awaitItem()
            assertThat(state.description).isEqualTo("Buy milk")
            assertThat(state.voiceRetry).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `voice empty shows retry dialog`() = runTest {
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            viewModel.onVoiceResult(VoiceResult.Empty)
            assertThat(awaitItem().voiceRetry).isNotNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `voice cancelled shows retry dialog`() = runTest {
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            viewModel.onVoiceResult(VoiceResult.Cancelled)
            assertThat(awaitItem().voiceRetry).isNotNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `voice error shows retry dialog with reason`() = runTest {
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            viewModel.onVoiceResult(VoiceResult.Error("Microphone unavailable"))
            val state = awaitItem()
            assertThat(state.voiceRetry?.message).contains("Microphone unavailable")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `second voice success after retry clears dialog and fills description`() = runTest {
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            viewModel.onVoiceResult(VoiceResult.Empty)
            awaitItem() // retry shown
            viewModel.onVoiceResult(VoiceResult.Success("Walk dog"))
            val state = awaitItem()
            assertThat(state.description).isEqualTo("Walk dog")
            assertThat(state.voiceRetry).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onDismissVoiceRetry clears retry state`() = runTest {
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            viewModel.onVoiceResult(VoiceResult.Cancelled)
            awaitItem()
            viewModel.onDismissVoiceRetry()
            assertThat(awaitItem().voiceRetry).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save after voice success creates task`() = runTest {
        val viewModel = vm()
        viewModel.uiState.test {
            awaitItem()
            viewModel.onVoiceResult(VoiceResult.Success("Pick up package"))
            awaitItem()
            viewModel.save()
            awaitItem() // isSaving=true
            assertThat(awaitItem().saved).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(tasksFlow.value).hasSize(1)
        assertThat(tasksFlow.value.first().description).isEqualTo("Pick up package")
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
