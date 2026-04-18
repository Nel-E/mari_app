package com.mari.wear.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mari.wear.settings.WearSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WearSettingsUiState(
    val reminderIntervalMinutes: Int = 30,
    val reminderEnabled: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: WearSettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<WearSettingsUiState> = settingsRepository.settings
        .map {
            WearSettingsUiState(
                reminderIntervalMinutes = it.reminderIntervalMinutes,
                reminderEnabled = it.reminderEnabled,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WearSettingsUiState())

    fun decrementReminderInterval() {
        viewModelScope.launch {
            settingsRepository.updateReminderIntervalMinutes(uiState.value.reminderIntervalMinutes - 1)
        }
    }

    fun incrementReminderInterval() {
        viewModelScope.launch {
            settingsRepository.updateReminderIntervalMinutes(uiState.value.reminderIntervalMinutes + 1)
        }
    }

    fun toggleReminderEnabled() {
        viewModelScope.launch {
            settingsRepository.updateReminderEnabled(!uiState.value.reminderEnabled)
        }
    }
}
