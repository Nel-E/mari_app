package com.mari.app.ui.screens.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mari.app.reminders.DeadlineReminderScheduler
import com.mari.app.settings.PhoneSettings
import com.mari.app.settings.SettingsReader
import com.mari.shared.data.repository.TaskRepository
import com.mari.shared.domain.Clock
import com.mari.shared.domain.DeadlineReminder
import com.mari.shared.domain.DeviceId
import com.mari.shared.domain.DueDateResolver
import com.mari.shared.domain.DueKind
import com.mari.shared.domain.DuePreset
import com.mari.shared.domain.ExecutionRules
import com.mari.shared.domain.TaskValidation
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddTaskUiState(
    val name: String = "",
    val description: String = "",
    val nameError: String? = null,
    val descriptionError: String? = null,
    val formError: String? = null,
    val duePreset: DuePreset? = null,
    val dueDateText: String = LocalDate.now().toString(),
    val dueTimeText: String = "",
    val dueMonthText: String = "",
    val dueYearText: String = "",
    val duePreview: String? = null,
    val colorHex: String = "",
    val colorError: String? = null,
    val reminderTemplates: List<DeadlineReminder> = PhoneSettings.DEFAULT_DEADLINE_REMINDER_TEMPLATES,
    val selectedReminderOffsets: Set<Long> = emptySet(),
    val isSaving: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class AddTaskViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val settingsRepository: SettingsReader,
    private val deadlineReminderScheduler: DeadlineReminderScheduler,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddTaskUiState())
    val uiState: StateFlow<AddTaskUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(reminderTemplates = settings.deadlineReminderTemplates.take(4)) }
            }
        }
    }

    fun onNameChange(text: String) {
        _uiState.update { it.copy(name = text, nameError = null, formError = null) }
    }

    fun onDescriptionChange(text: String) {
        _uiState.update { it.copy(description = text, descriptionError = null, formError = null) }
    }

    fun onDuePresetChange(preset: DuePreset?) {
        _uiState.update { it.copy(duePreset = preset, colorError = null, formError = null) }
        updateDuePreview()
    }

    fun onDueDateChange(text: String) {
        _uiState.update { it.copy(dueDateText = text, formError = null) }
        updateDuePreview()
    }

    fun onDueTimeChange(text: String) {
        _uiState.update { it.copy(dueTimeText = text, formError = null) }
        updateDuePreview()
    }

    fun onDueMonthChange(text: String) {
        _uiState.update { it.copy(dueMonthText = text, formError = null) }
        updateDuePreview()
    }

    fun onDueYearChange(text: String) {
        _uiState.update { it.copy(dueYearText = text, formError = null) }
        updateDuePreview()
    }

    fun onColorChange(text: String) {
        _uiState.update { it.copy(colorHex = text, colorError = null, formError = null) }
    }

    fun onReminderToggle(offsetSeconds: Long, enabled: Boolean) {
        _uiState.update { state ->
            val updated = if (enabled) state.selectedReminderOffsets + offsetSeconds else state.selectedReminderOffsets - offsetSeconds
            state.copy(selectedReminderOffsets = updated)
        }
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

        val dueSelection = resolveDueSelection(state)
        if (state.duePreset != null && dueSelection == null) {
            _uiState.update { it.copy(formError = "Enter a valid deadline") }
            return
        }
        if (dueSelection != null && state.colorHex.isBlank()) {
            _uiState.update { it.copy(colorError = "Choose a color for tasks with deadlines") }
            return
        }

        val validName = name.getOrThrow()
        val validDescription = description.getOrThrow()
        _uiState.update { it.copy(isSaving = true, formError = null) }
        viewModelScope.launch {
            val existing = repository.getTasks()
            val duplicate = TaskValidation.findDuplicateName(existing, validName)
            if (duplicate != null) {
                _uiState.update { it.copy(isSaving = false, nameError = "A task named \"$duplicate\" already exists") }
                return@launch
            }

            val selectedReminders = state.reminderTemplates.filter { it.offsetSeconds in state.selectedReminderOffsets }
            lateinit var createdTask: com.mari.shared.domain.Task
            val result = repository.update { tasks ->
                createdTask = ExecutionRules.createTask(
                    name = validName,
                    description = validDescription,
                    clock = clock,
                    deviceId = DeviceId.PHONE,
                    dueAt = dueSelection?.second,
                    dueKind = dueSelection?.first,
                    deadlineReminders = selectedReminders,
                    colorHex = state.colorHex.ifBlank { null },
                )
                tasks + createdTask
            }
            if (result.isSuccess) {
                if (createdTask.dueAt != null) deadlineReminderScheduler.schedule(createdTask)
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Failed to save task"
                _uiState.update { it.copy(isSaving = false, formError = msg) }
            }
        }
    }

    private fun updateDuePreview() {
        val preview = resolveDueSelection(_uiState.value)?.second?.toString()
        _uiState.update { it.copy(duePreview = preview) }
    }

    private fun resolveDueSelection(state: AddTaskUiState): Pair<DueKind, Instant>? {
        val preset = state.duePreset ?: return null
        return runCatching {
            when (preset) {
                DuePreset.SPECIFIC_DAY -> {
                    val date = LocalDate.parse(state.dueDateText)
                    val time = state.dueTimeText.takeIf { it.isNotBlank() }?.let(LocalTime::parse)
                    DueDateResolver.resolve(
                        preset = preset,
                        specificDate = date,
                        specificTime = time,
                        zoneId = ZoneId.systemDefault(),
                        now = clock.nowUtc(),
                    )
                }
                DuePreset.MONTH_YEAR -> DueDateResolver.resolve(
                    preset = preset,
                    month = state.dueMonthText.toInt(),
                    year = state.dueYearText.toInt(),
                    zoneId = ZoneId.systemDefault(),
                    now = clock.nowUtc(),
                )
                else -> DueDateResolver.resolve(
                    preset = preset,
                    zoneId = ZoneId.systemDefault(),
                    now = clock.nowUtc(),
                )
            }
        }.getOrNull()
    }
}
