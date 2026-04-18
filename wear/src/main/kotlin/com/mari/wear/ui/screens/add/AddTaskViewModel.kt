package com.mari.wear.ui.screens.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class WearAddTaskUiState(
    val description: String = "",
    val saved: Boolean = false,
    val voiceError: String? = null,
)

@HiltViewModel
class AddTaskViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WearAddTaskUiState())
    val uiState: StateFlow<WearAddTaskUiState> = _uiState.asStateFlow()

    fun onVoiceResult(text: String) {
        _uiState.update { it.copy(description = text, voiceError = null) }
        save(text)
    }

    fun onVoiceEmpty() {
        _uiState.update { it.copy(voiceError = "No speech detected. Try again.") }
    }

    fun onVoiceCancelled() {
        _uiState.update { it.copy(voiceError = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(voiceError = null) }
    }

    private fun save(description: String) {
        val error = TaskValidation.validateDescription(description)
        if (error != null) {
            _uiState.update { it.copy(voiceError = error) }
            return
        }
        viewModelScope.launch {
            repository.update { tasks ->
                tasks + ExecutionRules.createTask(description, clock, DeviceId.WATCH)
            }
            _uiState.update { it.copy(saved = true) }
        }
    }
}
