package com.mari.app.ui.screens.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mari.app.voice.VoiceResult
import com.mari.shared.data.repository.TaskRepository
import com.mari.shared.domain.Clock
import com.mari.shared.domain.DeviceId
import com.mari.shared.domain.ExecutionRules
import com.mari.shared.domain.TaskValidation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VoiceRetryState(val message: String)

data class AddTaskUiState(
    val description: String = "",
    val descriptionError: String? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val voiceRetry: VoiceRetryState? = null,
)

@HiltViewModel
class AddTaskViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddTaskUiState())
    val uiState: StateFlow<AddTaskUiState> = _uiState.asStateFlow()

    fun onDescriptionChange(text: String) {
        _uiState.update { it.copy(description = text, descriptionError = null) }
    }

    fun onVoiceResult(result: VoiceResult) {
        when (result) {
            is VoiceResult.Success -> _uiState.update {
                it.copy(description = result.text, voiceRetry = null)
            }
            is VoiceResult.Empty -> _uiState.update {
                it.copy(voiceRetry = VoiceRetryState("No speech detected. Try again?"))
            }
            is VoiceResult.Cancelled -> _uiState.update {
                it.copy(voiceRetry = VoiceRetryState("Voice input was cancelled. Try again?"))
            }
            is VoiceResult.Error -> _uiState.update {
                it.copy(voiceRetry = VoiceRetryState(result.reason))
            }
        }
    }

    fun onDismissVoiceRetry() {
        _uiState.update { it.copy(voiceRetry = null) }
    }

    fun save() {
        val raw = _uiState.value.description
        val validated = TaskValidation.validateDescription(raw)
        if (validated.isFailure) {
            _uiState.update {
                it.copy(descriptionError = validated.exceptionOrNull()?.message ?: "Invalid description")
            }
            return
        }
        val description = validated.getOrThrow()
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            repository.update { tasks ->
                tasks + ExecutionRules.createTask(
                    description = description,
                    clock = clock,
                    deviceId = DeviceId.PHONE,
                )
            }
            _uiState.update { it.copy(isSaving = false, saved = true) }
        }
    }
}
