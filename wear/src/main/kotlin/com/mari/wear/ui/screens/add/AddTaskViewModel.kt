package com.mari.wear.ui.screens.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mari.shared.data.repository.TaskRepository
import com.mari.shared.domain.Clock
import com.mari.shared.domain.DeviceId
import com.mari.shared.domain.ExecutionRules
import com.mari.shared.domain.TaskValidation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WearAddTaskUiState(
    val name: String = "",
    val description: String = "",
    val nameError: String? = null,
    val descriptionError: String? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class AddTaskViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WearAddTaskUiState())
    val uiState: StateFlow<WearAddTaskUiState> = _uiState.asStateFlow()

    fun onNameChange(text: String) {
        _uiState.update { it.copy(name = text, nameError = null) }
    }

    fun onDescriptionChange(text: String) {
        _uiState.update { it.copy(description = text, descriptionError = null) }
    }

    fun save() {
        val state = _uiState.value
        val name = TaskValidation.validateName(state.name)
        val description = TaskValidation.validateDescription(state.description)
        if (name.isFailure || description.isFailure) {
            _uiState.update {
                it.copy(
                    nameError = name.exceptionOrNull()?.message,
                    descriptionError = description.exceptionOrNull()?.message,
                )
            }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = repository.update { tasks ->
                tasks + ExecutionRules.createTask(
                    name = name.getOrThrow(),
                    description = description.getOrThrow(),
                    clock = clock,
                    deviceId = DeviceId.WATCH,
                )
            }
            if (result.isSuccess) {
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Failed to save task"
                _uiState.update { it.copy(isSaving = false, nameError = msg) }
            }
        }
    }
}
