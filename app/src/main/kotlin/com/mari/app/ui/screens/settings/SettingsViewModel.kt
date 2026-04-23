package com.mari.app.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mari.app.appupdate.AppUpdateScheduler
import com.mari.app.data.repository.FileTaskRepository
import com.mari.app.data.storage.SafFolderManager
import com.mari.app.data.storage.SafGrant
import com.mari.app.domain.model.AppUpdateInfo
import com.mari.app.domain.model.UpdateTrack
import com.mari.app.domain.repository.AppUpdateRepository
import com.mari.app.reminders.DailyNudgeScheduler
import com.mari.app.settings.SettingsRepository
import com.mari.shared.domain.DeadlineReminder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val storageFolderLabel: String = "Not set",
    val shakeStrength: Float = 15f,
    val shakeDurationMs: Long = 300L,
    val shakeVibrate: Boolean = true,
    val reminderEnabled: Boolean = false,
    val reminderIntervalMinutes: Int = 30,
    val reminderVibrate: Boolean = true,
    val quietHoursLabel: String = "22:00 - 07:00",
    val deadlineReminderTemplates: List<DeadlineReminder> = emptyList(),
    val backupInfo: BackupInfo = BackupInfo(),
    val dailyNudgeEnabled: Boolean = false,
    val dailyNudgeHour: Int = 9,
    val dailyNudgeMinute: Int = 0,
    val updateAutoCheckEnabled: Boolean = true,
    val updateTrack: UpdateTrack = UpdateTrack.STABLE,
    val availableUpdate: AppUpdateInfo? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val safFolderManager: SafFolderManager,
    private val fileTaskRepository: FileTaskRepository,
    private val dailyNudgeScheduler: DailyNudgeScheduler,
    private val appUpdateRepository: AppUpdateRepository,
    private val appUpdateScheduler: AppUpdateScheduler,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        safFolderManager.grant,
        appUpdateRepository.state,
    ) { settings, grant, updateState ->
        SettingsUiState(
            storageFolderLabel = when (grant) {
                is SafGrant.Granted -> grant.treeUri.toString()
                SafGrant.Missing -> "Not set"
            },
            shakeStrength = settings.shakeStrength,
            shakeDurationMs = settings.shakeDurationMs,
            shakeVibrate = settings.shakeVibrate,
            reminderEnabled = settings.reminderEnabled,
            reminderIntervalMinutes = settings.reminderIntervalMinutes,
            reminderVibrate = settings.reminderVibrate,
            quietHoursLabel = "%02d:%02d - %02d:%02d".format(
                settings.quietStartHour,
                settings.quietStartMinute,
                settings.quietEndHour,
                settings.quietEndMinute,
            ),
            deadlineReminderTemplates = settings.deadlineReminderTemplates,
            backupInfo = backupInfoFor(grant),
            dailyNudgeEnabled = settings.dailyNudgeEnabled,
            dailyNudgeHour = settings.dailyNudgeHour,
            dailyNudgeMinute = settings.dailyNudgeMinute,
            updateAutoCheckEnabled = updateState.autoCheckEnabled,
            updateTrack = updateState.track,
            availableUpdate = updateState.availableUpdate,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onShakeStrengthChange(value: Float) {
        viewModelScope.launch { settingsRepository.updateShakeStrength(value) }
    }

    fun onShakeDurationChange(value: Float) {
        viewModelScope.launch { settingsRepository.updateShakeDuration(value.toLong()) }
    }

    fun onShakeVibrateChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateShakeVibrate(enabled) }
    }

    fun onReminderEnabledChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateReminderEnabled(enabled) }
    }

    fun onReminderIntervalChange(value: Float) {
        viewModelScope.launch { settingsRepository.updateReminderIntervalMinutes(value.toInt()) }
    }

    fun onReminderVibrateChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateReminderVibrate(enabled) }
    }

    fun shiftDeadlineTemplate(index: Int, deltaSeconds: Long) {
        viewModelScope.launch {
            val current = settingsRepository.current().deadlineReminderTemplates.toMutableList()
            if (index !in current.indices) return@launch
            val item = current[index]
            current[index] = item.copy(offsetSeconds = item.offsetSeconds + deltaSeconds)
            settingsRepository.updateDeadlineReminderTemplates(current)
        }
    }

    fun shiftQuietHours(hoursDelta: Int) {
        viewModelScope.launch {
            val current = settingsRepository.current()
            settingsRepository.updateQuietHours(
                startHour = (current.quietStartHour + hoursDelta).floorMod(24),
                startMinute = current.quietStartMinute,
                endHour = (current.quietEndHour + hoursDelta).floorMod(24),
                endMinute = current.quietEndMinute,
            )
        }
    }

    fun onDailyNudgeEnabledChange(enabled: Boolean) {
        viewModelScope.launch {
            val current = settingsRepository.current()
            settingsRepository.updateDailyNudge(enabled, current.dailyNudgeHour, current.dailyNudgeMinute)
            if (enabled) {
                dailyNudgeScheduler.schedule(current.dailyNudgeHour, current.dailyNudgeMinute, current.quietWindow)
            } else {
                dailyNudgeScheduler.cancel()
            }
        }
    }

    fun onDailyNudgeTimeChange(hour: Int, minute: Int) {
        viewModelScope.launch {
            val current = settingsRepository.current()
            settingsRepository.updateDailyNudge(current.dailyNudgeEnabled, hour, minute)
            if (current.dailyNudgeEnabled) {
                dailyNudgeScheduler.cancel()
                dailyNudgeScheduler.schedule(hour, minute, current.quietWindow)
            }
        }
    }

    fun onUpdateAutoCheckChange(enabled: Boolean) {
        viewModelScope.launch {
            appUpdateRepository.setAutoCheckEnabled(enabled)
            if (enabled) appUpdateScheduler.enqueuePeriodic() else appUpdateScheduler.cancelAll()
        }
    }

    fun onUpdateTrackChange(track: UpdateTrack) {
        viewModelScope.launch {
            appUpdateRepository.setTrack(track)
            appUpdateScheduler.reenqueueOnTrackChange(track)
        }
    }

    fun onCheckNow() {
        appUpdateScheduler.enqueueManual()
    }

    fun onFolderPicked(uri: Uri) {
        viewModelScope.launch {
            safFolderManager.releaseAndClear()
            safFolderManager.onFolderPicked(uri)
            fileTaskRepository.onGrantAcquired()
        }
    }

    private fun backupInfoFor(grant: SafGrant): BackupInfo {
        val nextBackup = ZonedDateTime.now(ZoneOffset.UTC)
            .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
        if (grant !is SafGrant.Granted) return BackupInfo(nextBackupLabel = nextBackup)
        val root = DocumentFile.fromTreeUri(context, grant.treeUri)
        val latest = root?.findFile("backups")
            ?.listFiles()
            ?.filter { it.name?.startsWith("mari_tasks.") == true }
            ?.maxByOrNull { it.name ?: "" }
            ?.name
            ?: "No weekly backups yet"
        return BackupInfo(lastBackupLabel = latest, nextBackupLabel = nextBackup)
    }
}

private fun Int.floorMod(mod: Int): Int = ((this % mod) + mod) % mod
