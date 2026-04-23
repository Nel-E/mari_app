package com.mari.app.appupdate

import android.app.NotificationManager
import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import com.mari.app.data.repository.AppUpdateLocalStore
import com.mari.app.domain.model.AppUpdateInfo
import com.mari.app.domain.model.AppUpdateLocalState
import com.mari.app.domain.model.UpdateTrack
import com.mari.app.domain.repository.AppUpdateRepository
import com.mari.app.wearinstall.WearUpdatePusher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUpdateCheckWorkerTest {

    private val ctx get() = RuntimeEnvironment.getApplication()
    private val json = Json { ignoreUnknownKeys = true }

    private fun notificationCount(): Int {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return shadowOf(nm).allNotifications.size
    }

    @Test
    fun `autoCheckEnabled false and forceNotify false skips network call`() = runTest {
        val repo = FakeAppUpdateRepository(
            initialState = AppUpdateLocalState(autoCheckEnabled = false),
        )
        val notifier = realNotifier()

        val worker = buildWorker(forceNotify = false, repo = repo, notifier = notifier)
        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        assertThat(repo.checkForUpdateCalled).isFalse()
        assertThat(notificationCount()).isEqualTo(0)
    }

    @Test
    fun `forceNotify true fetches even when autoCheck disabled`() = runTest {
        val update = makeUpdateInfo(versionCode = 2)
        val repo = FakeAppUpdateRepository(
            initialState = AppUpdateLocalState(
                autoCheckEnabled = false,
                availableUpdate = update,
                lastNotifiedVersionCode = null,
            ),
        )
        val worker = buildWorker(forceNotify = true, repo = repo, notifier = realNotifier())
        worker.doWork()

        assertThat(repo.checkForUpdateCalled).isTrue()
    }

    @Test
    fun `notifies when update available and not already notified`() = runTest {
        val update = makeUpdateInfo(versionCode = 2)
        val repo = FakeAppUpdateRepository(
            initialState = AppUpdateLocalState(
                availableUpdate = update,
                lastNotifiedVersionCode = null,
            ),
        )
        buildWorker(forceNotify = false, repo = repo, notifier = realNotifier()).doWork()

        assertThat(notificationCount()).isEqualTo(1)
    }

    @Test
    fun `dedup - same version already notified without forceNotify - skips notification`() = runTest {
        val update = makeUpdateInfo(versionCode = 2)
        val repo = FakeAppUpdateRepository(
            initialState = AppUpdateLocalState(
                availableUpdate = update,
                lastNotifiedVersionCode = 2,
            ),
        )
        buildWorker(forceNotify = false, repo = repo, notifier = realNotifier()).doWork()

        assertThat(notificationCount()).isEqualTo(0)
    }

    @Test
    fun `forceNotify true notifies even when already notified for same version`() = runTest {
        val update = makeUpdateInfo(versionCode = 2)
        val repo = FakeAppUpdateRepository(
            initialState = AppUpdateLocalState(
                availableUpdate = update,
                lastNotifiedVersionCode = 2,
            ),
        )
        buildWorker(forceNotify = true, repo = repo, notifier = realNotifier()).doWork()

        assertThat(notificationCount()).isEqualTo(1)
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun realNotifier() = AppUpdateNotifier(ctx, AppUpdateLocalStore(ctx, json))

    private fun buildWorker(
        forceNotify: Boolean,
        repo: AppUpdateRepository,
        notifier: AppUpdateNotifier,
        wearDispatcher: WearUpdatePusher = FakeWearUpdatePusher(),
    ): AppUpdateCheckWorker {
        val inputData = androidx.work.Data.Builder()
            .putBoolean(AppUpdateCheckWorker.KEY_FORCE_NOTIFY, forceNotify)
            .build()
        return TestListenableWorkerBuilder<AppUpdateCheckWorker>(ctx)
            .setInputData(inputData)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = AppUpdateCheckWorker(appContext, workerParameters, repo, notifier, wearDispatcher)
            })
            .build() as AppUpdateCheckWorker
    }

    private class FakeWearUpdatePusher : WearUpdatePusher {
        override suspend fun pushServerUpdateIfNeeded(state: AppUpdateLocalState) = Unit
    }

    private fun makeUpdateInfo(versionCode: Int) = AppUpdateInfo(
        track = "stable", component = "phone", packageName = "com.mari.app",
        versionCode = versionCode, versionName = "1.0.$versionCode",
        fileName = "mari-$versionCode.apk", fileSizeBytes = 1_000_000L,
        sha256 = "abc123", releasedAt = "2026-01-01T00:00:00Z",
        notificationTitle = "Update available",
        notificationText = "Version 1.0.$versionCode is ready",
        changelog = null, downloadUrl = "http://192.168.1.50:8000/api/app-update/artifacts/stable/phone/mari-$versionCode.apk",
    )
}

// ---------------------------------------------------------------------------
// FakeAppUpdateRepository
// ---------------------------------------------------------------------------

class FakeAppUpdateRepository(
    initialState: AppUpdateLocalState = AppUpdateLocalState(),
) : AppUpdateRepository {

    private val _state = MutableStateFlow(initialState)
    override val state: StateFlow<AppUpdateLocalState> = _state

    var checkForUpdateCalled = false

    override suspend fun checkForUpdate(forceNotify: Boolean) {
        checkForUpdateCalled = true
    }

    override suspend fun setAutoCheckEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(autoCheckEnabled = enabled)
    }

    override suspend fun setTrack(track: UpdateTrack) {
        _state.value = _state.value.copy(
            track = track,
            availableUpdate = null,
            lastNotifiedVersionCode = null,
        )
    }

    override suspend fun setCheckInterval(hours: Int) {
        _state.value = _state.value.copy(checkIntervalHours = hours)
    }

    override suspend fun acknowledgeUpdate(versionCode: Int) {
        _state.value = _state.value.copy(lastAcknowledgedVersionCode = versionCode)
    }

    override suspend fun clearAvailableUpdate() {
        _state.value = _state.value.copy(availableUpdate = null)
    }

    override suspend fun setLastNotifiedVersionCode(code: Int?) {
        _state.value = _state.value.copy(lastNotifiedVersionCode = code)
    }
}
