package com.mari.app.ui.screens.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mari.app.appupdate.AppUpdateInstaller
import com.mari.app.domain.model.AppUpdateInfo
import com.mari.app.domain.model.AppUpdateReleaseNote
import com.mari.app.domain.repository.AppUpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UpdateAvailableUiState(
    val update: AppUpdateInfo? = null,
    val releaseNotes: List<AppUpdateReleaseNote> = emptyList(),
    val downloadState: DownloadState = DownloadState.Idle,
)

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val progress: Float) : DownloadState
    data class Ready(val file: File) : DownloadState
    data class Error(val message: String) : DownloadState
}

@HiltViewModel
class UpdateAvailableViewModel @Inject constructor(
    private val appUpdateRepository: AppUpdateRepository,
    private val installer: AppUpdateInstaller,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateAvailableUiState())
    val uiState: StateFlow<UpdateAvailableUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            appUpdateRepository.state.collect { repoState ->
                _uiState.update { it.copy(update = repoState.availableUpdate, releaseNotes = repoState.releaseNotes) }
            }
        }
    }

    fun download() {
        val update = _uiState.value.update ?: return
        if (_uiState.value.downloadState is DownloadState.Downloading) return

        viewModelScope.launch {
            _uiState.update { it.copy(downloadState = DownloadState.Downloading(0f)) }
            val result = installer.downloadAndVerify(
                track = update.track,
                component = update.component,
                fileName = update.fileName,
                expectedSha256 = update.sha256,
            ) { downloaded, total ->
                val progress = if (total > 0) downloaded.toFloat() / total else 0f
                _uiState.update { it.copy(downloadState = DownloadState.Downloading(progress)) }
            }
            result.fold(
                onSuccess = { file -> _uiState.update { it.copy(downloadState = DownloadState.Ready(file)) } },
                onFailure = { e -> _uiState.update { it.copy(downloadState = DownloadState.Error(e.message ?: "Download failed")) } },
            )
        }
    }

    fun install(file: File) {
        context.startActivity(installer.buildInstallIntent(file))
    }

    fun acknowledge() {
        val versionCode = _uiState.value.update?.versionCode ?: return
        viewModelScope.launch { appUpdateRepository.acknowledgeUpdate(versionCode) }
    }

    fun resetDownload() {
        _uiState.update { it.copy(downloadState = DownloadState.Idle) }
    }
}
