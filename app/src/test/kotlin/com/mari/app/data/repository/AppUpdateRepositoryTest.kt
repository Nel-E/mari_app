package com.mari.app.data.repository

import com.google.common.truth.Truth.assertThat
import com.mari.app.data.remote.AppUpdateApiService
import com.mari.app.data.remote.dto.AppUpdateLatestDto
import com.mari.app.data.remote.dto.AppUpdateReleaseNoteDto
import com.mari.app.domain.model.UpdateTrack
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Response

// BuildConfig.VERSION_CODE == 1 in test builds.

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUpdateRepositoryTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `higher versionCode sets availableUpdate`() = runTest {
        val fakeApi = FakeAppUpdateApiService().apply { latestDto = makeDto(versionCode = 2) }
        val repo = AppUpdateRepositoryImpl(fakeApi, AppUpdateLocalStore(context, json), backgroundScope)

        repo.checkForUpdate(forceNotify = false)
        advanceUntilIdle()

        val state = repo.state.first()
        assertThat(state.availableUpdate).isNotNull()
        assertThat(state.availableUpdate?.versionCode).isEqualTo(2)
    }

    @Test
    fun `same versionCode clears availableUpdate`() = runTest {
        val fakeApi = FakeAppUpdateApiService().apply { latestDto = makeDto(versionCode = 1) }
        val repo = AppUpdateRepositoryImpl(fakeApi, AppUpdateLocalStore(context, json), backgroundScope)

        repo.checkForUpdate(forceNotify = false)
        advanceUntilIdle()

        assertThat(repo.state.first().availableUpdate).isNull()
    }

    @Test
    fun `lower versionCode clears availableUpdate`() = runTest {
        val fakeApi = FakeAppUpdateApiService().apply { latestDto = makeDto(versionCode = 0) }
        val repo = AppUpdateRepositoryImpl(fakeApi, AppUpdateLocalStore(context, json), backgroundScope)

        repo.checkForUpdate(forceNotify = false)
        advanceUntilIdle()

        assertThat(repo.state.first().availableUpdate).isNull()
    }

    @Test
    fun `minInstalledVersionCode above current filters out update`() = runTest {
        // BuildConfig.VERSION_CODE == 1; minInstalled == 5 means device is too old
        val fakeApi = FakeAppUpdateApiService().apply {
            latestDto = makeDto(versionCode = 2, minInstalledVersionCode = 5)
        }
        val repo = AppUpdateRepositoryImpl(fakeApi, AppUpdateLocalStore(context, json), backgroundScope)

        repo.checkForUpdate(forceNotify = false)
        advanceUntilIdle()

        assertThat(repo.state.first().availableUpdate).isNull()
    }

    @Test
    fun `setTrack clears availableUpdate and lastNotifiedVersionCode`() = runTest {
        val fakeApi = FakeAppUpdateApiService().apply { latestDto = makeDto(versionCode = 2) }
        val repo = AppUpdateRepositoryImpl(fakeApi, AppUpdateLocalStore(context, json), backgroundScope)

        repo.checkForUpdate(forceNotify = false)
        advanceUntilIdle()
        assertThat(repo.state.first().availableUpdate).isNotNull()

        repo.setTrack(UpdateTrack.BETA)
        advanceUntilIdle()

        val state = repo.state.first()
        assertThat(state.availableUpdate).isNull()
        assertThat(state.lastNotifiedVersionCode).isNull()
    }

    private fun makeDto(
        versionCode: Int,
        minInstalledVersionCode: Int? = null,
    ) = AppUpdateLatestDto(
        component = "phone",
        track = "stable",
        packageName = "com.mari.app",
        versionCode = versionCode,
        versionName = "1.0.$versionCode",
        fileName = "mari-$versionCode.apk",
        fileSizeBytes = 1_000_000L,
        sha256 = "abc123",
        releasedAt = "2026-01-01T00:00:00Z",
        notificationTitle = "Update available",
        notificationText = "Version 1.0.$versionCode is ready",
        changelog = null,
        minInstalledVersionCode = minInstalledVersionCode,
    )
}

class FakeAppUpdateApiService : AppUpdateApiService {
    var latestDto: AppUpdateLatestDto = AppUpdateLatestDto(
        component = "phone", track = "stable", packageName = "com.mari.app",
        versionCode = 2, versionName = "1.0.2", fileName = "mari-2.apk",
        fileSizeBytes = 1_000_000L, sha256 = "abc123",
        releasedAt = "2026-01-01T00:00:00Z",
        notificationTitle = "Update", notificationText = "Update available",
    )
    var releaseNotes: List<AppUpdateReleaseNoteDto> = emptyList()
    var downloadResponse: Response<ResponseBody> = Response.error(404, okhttp3.ResponseBody.create(null, ""))

    override suspend fun getLatest(track: String, component: String) = latestDto

    override suspend fun getReleases(
        track: String,
        component: String,
        after: Int,
    ) = releaseNotes

    override suspend fun downloadArtifact(
        track: String,
        component: String,
        fileName: String,
    ) = downloadResponse
}
