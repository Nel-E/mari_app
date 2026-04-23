package com.mari.app.data.repository

import com.google.common.truth.Truth.assertThat
import com.mari.app.domain.model.AppUpdateInfo
import com.mari.app.domain.model.UpdateTrack
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUpdateLocalStoreTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var store: AppUpdateLocalStore

    @Before
    fun setUp() = runBlocking {
        store = AppUpdateLocalStore(context, json)
        store.setAutoCheckEnabled(true)
        store.setTrack(UpdateTrack.STABLE)
        store.setCheckInterval(6)
        store.setAvailableUpdate(null)
        store.setLastNotifiedVersionCode(null)
        store.setLastAcknowledgedVersionCode(null)
    }

    @Test
    fun `default state has expected values`() = runTest {
        val state = store.state.first()
        assertThat(state.autoCheckEnabled).isTrue()
        assertThat(state.track).isEqualTo(UpdateTrack.STABLE)
        assertThat(state.checkIntervalHours).isEqualTo(6)
        assertThat(state.availableUpdate).isNull()
        assertThat(state.lastNotifiedVersionCode).isNull()
    }

    @Test
    fun `setTrack persists BETA`() = runTest {
        store.setTrack(UpdateTrack.BETA)
        val state = store.state.first()
        assertThat(state.track).isEqualTo(UpdateTrack.BETA)
    }

    @Test
    fun `setAutoCheckEnabled persists false`() = runTest {
        store.setAutoCheckEnabled(false)
        val state = store.state.first()
        assertThat(state.autoCheckEnabled).isFalse()
    }

    @Test
    fun `setCheckInterval clamps to at least 1`() = runTest {
        store.setCheckInterval(0)
        val state = store.state.first()
        assertThat(state.checkIntervalHours).isEqualTo(1)
    }

    @Test
    fun `setAvailableUpdate persists and retrieves info`() = runTest {
        val info = makeUpdateInfo(versionCode = 5)
        store.setAvailableUpdate(info)
        val state = store.state.first()
        assertThat(state.availableUpdate).isNotNull()
        assertThat(state.availableUpdate?.versionCode).isEqualTo(5)
        assertThat(state.availableUpdate?.sha256).isEqualTo(info.sha256)
    }

    @Test
    fun `setAvailableUpdate null clears persisted info`() = runTest {
        store.setAvailableUpdate(makeUpdateInfo(versionCode = 5))
        store.setAvailableUpdate(null)
        val state = store.state.first()
        assertThat(state.availableUpdate).isNull()
    }

    @Test
    fun `setLastNotifiedVersionCode persists code`() = runTest {
        store.setLastNotifiedVersionCode(7)
        val state = store.state.first()
        assertThat(state.lastNotifiedVersionCode).isEqualTo(7)
    }

    @Test
    fun `setLastNotifiedVersionCode null clears code`() = runTest {
        store.setLastNotifiedVersionCode(7)
        store.setLastNotifiedVersionCode(null)
        val state = store.state.first()
        assertThat(state.lastNotifiedVersionCode).isNull()
    }

    @Test
    fun `setReleaseNotes persists list`() = runTest {
        val notes = listOf(
            com.mari.app.domain.model.AppUpdateReleaseNote(
                versionCode = 2, versionName = "1.0.2",
                releasedAt = "2026-01-01T00:00:00Z",
                features = listOf("New feature"), upgrades = emptyList(), fixes = emptyList(),
            ),
        )
        store.setReleaseNotes(notes)
        val state = store.state.first()
        assertThat(state.releaseNotes).hasSize(1)
        assertThat(state.releaseNotes.first().versionCode).isEqualTo(2)
    }

    // ---------------------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------------------

    private fun makeUpdateInfo(versionCode: Int) = AppUpdateInfo(
        track = "stable", component = "phone", packageName = "com.mari.app",
        versionCode = versionCode, versionName = "1.0.$versionCode",
        fileName = "mari-$versionCode.apk", fileSizeBytes = 1_000_000L,
        sha256 = "deadbeef$versionCode",
        releasedAt = "2026-01-01T00:00:00Z",
        notificationTitle = "Update", notificationText = "Ready",
        changelog = null, downloadUrl = "http://example.com/mari.apk",
    )
}
