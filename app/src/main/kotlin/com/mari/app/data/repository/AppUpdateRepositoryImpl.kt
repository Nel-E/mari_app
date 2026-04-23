package com.mari.app.data.repository

import com.mari.app.BuildConfig
import com.mari.app.data.remote.AppUpdateApiService
import com.mari.app.data.remote.dto.AppUpdateLatestDto
import com.mari.app.data.remote.dto.AppUpdateReleaseNoteDto
import com.mari.app.di.ApplicationScope
import com.mari.app.domain.model.AppUpdateInfo
import com.mari.app.domain.model.AppUpdateLocalState
import com.mari.app.domain.model.AppUpdateReleaseNote
import com.mari.app.domain.model.UpdateTrack
import com.mari.app.domain.repository.AppUpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateRepositoryImpl @Inject constructor(
    private val api: AppUpdateApiService,
    private val localStore: AppUpdateLocalStore,
    @ApplicationScope private val scope: CoroutineScope,
) : AppUpdateRepository {

    override val state: StateFlow<AppUpdateLocalState> = localStore.state.stateIn(
        scope,
        SharingStarted.Eagerly,
        AppUpdateLocalState(),
    )

    override suspend fun checkForUpdate(forceNotify: Boolean) {
        val current = state.value
        val track = current.track
        runCatching {
            val dto = api.getLatest(track.wire, "phone")
            localStore.setLastCheckAt(Instant.now().toString())

            if (!isEligible(dto)) {
                localStore.setAvailableUpdate(null)
                return
            }

            val releaseNotes = runCatching {
                api.getReleases(track.wire, "phone", BuildConfig.VERSION_CODE)
                    .map { it.toReleaseNote() }
            }.getOrDefault(emptyList())

            val downloadUrl = "${BuildConfig.MARI_API_BASE_URL.trimEnd('/')}/" +
                "api/app-update/artifacts/${track.wire}/phone/${dto.fileName}"

            val info = dto.toAppUpdateInfo(downloadUrl)
            localStore.setAvailableUpdate(info)
            localStore.setReleaseNotes(releaseNotes)
        }
    }

    private fun isEligible(dto: AppUpdateLatestDto): Boolean {
        if (dto.versionCode <= BuildConfig.VERSION_CODE) return false
        val min = dto.minInstalledVersionCode
        if (min != null && BuildConfig.VERSION_CODE < min) return false
        return true
    }

    override suspend fun setAutoCheckEnabled(enabled: Boolean) =
        localStore.setAutoCheckEnabled(enabled)

    override suspend fun setTrack(track: UpdateTrack) {
        localStore.setTrack(track)
        localStore.setLastNotifiedVersionCode(null)
        localStore.setAvailableUpdate(null)
    }

    override suspend fun setCheckInterval(hours: Int) =
        localStore.setCheckInterval(hours)

    override suspend fun acknowledgeUpdate(versionCode: Int) =
        localStore.setLastAcknowledgedVersionCode(versionCode)

    override suspend fun clearAvailableUpdate() =
        localStore.setAvailableUpdate(null)

    override suspend fun setLastNotifiedVersionCode(code: Int?) =
        localStore.setLastNotifiedVersionCode(code)
}

// ---------------------------------------------------------------------------
// Mapping extensions
// ---------------------------------------------------------------------------

private fun AppUpdateLatestDto.toAppUpdateInfo(downloadUrl: String) = AppUpdateInfo(
    track = track,
    component = component,
    packageName = packageName,
    versionCode = versionCode,
    versionName = versionName,
    fileName = fileName,
    fileSizeBytes = fileSizeBytes,
    sha256 = sha256,
    releasedAt = releasedAt,
    notificationTitle = notificationTitle,
    notificationText = notificationText,
    changelog = changelog,
    downloadUrl = downloadUrl,
    minInstalledVersionCode = minInstalledVersionCode,
)

private fun AppUpdateReleaseNoteDto.toReleaseNote() = AppUpdateReleaseNote(
    versionCode = versionCode,
    versionName = versionName,
    releasedAt = releasedAt,
    features = features,
    upgrades = upgrades,
    fixes = fixes,
)
