package com.mari.app.wearinstall

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.mari.app.appupdate.AppUpdateInstaller
import com.mari.app.data.remote.AppUpdateApiService
import com.mari.app.data.repository.AppUpdateLocalStore
import com.mari.app.domain.model.AppUpdateLocalState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WearApkDispatcher"
private const val APK_ASSET_NAME = "wear-debug.apk"
private const val DATA_PATH = "/mari/wear/apk"

@Singleton
class WearApkDispatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: AppUpdateApiService,
    private val installer: AppUpdateInstaller,
    private val localStore: AppUpdateLocalStore,
) : WearUpdatePusher {
    private val dataClient by lazy { Wearable.getDataClient(context) }

    /** Pushes a bundled wear APK from assets — used during first-run / sideload. */
    suspend fun dispatchIfAvailable() {
        val apkBytes = try {
            context.assets.open(APK_ASSET_NAME).use { it.readBytes() }
        } catch (e: Exception) {
            Log.d(TAG, "Wear APK asset not found — skipping dispatch")
            return
        }

        val sha256 = sha256Hex(apkBytes)
        push(apkBytes, sha256)
    }

    /**
     * Checks the server for a newer wear APK and pushes it to the watch if found.
     * Tracks the last-pushed version so repeated checks don't re-send the same build.
     */
    override suspend fun pushServerUpdateIfNeeded(state: AppUpdateLocalState) {
        runCatching {
            val dto = api.getLatest(state.track.wire, "wear")
            val lastPushed = state.lastPushedWearVersionCode ?: 0
            if (dto.versionCode <= lastPushed) {
                Log.d(TAG, "Wear APK versionCode ${dto.versionCode} already pushed — skipping")
                return
            }

            Log.i(TAG, "Newer wear APK found (${dto.versionCode}) — downloading")
            val apkFile = installer.downloadAndVerify(
                track = state.track.wire,
                component = "wear",
                fileName = dto.fileName,
                expectedSha256 = dto.sha256,
            ).getOrThrow()

            try {
                push(apkFile.readBytes(), dto.sha256)
                localStore.setLastPushedWearVersionCode(dto.versionCode)
                Log.i(TAG, "Wear APK ${dto.versionCode} pushed to watch")
            } finally {
                apkFile.delete()
            }
        }.onFailure { e ->
            Log.w(TAG, "Wear server update push failed", e)
        }
    }

    private suspend fun push(apkBytes: ByteArray, sha256: String) {
        val asset = Asset.createFromBytes(apkBytes)
        val request = PutDataMapRequest.create("$DATA_PATH/${System.currentTimeMillis()}").apply {
            dataMap.putAsset("apk", asset)
            dataMap.putString("sha256", sha256)
        }.asPutDataRequest().setUrgent()

        runCatching { dataClient.putDataItem(request).await() }
            .onSuccess { Log.i(TAG, "Wear APK dispatched ($sha256)") }
            .onFailure { Log.w(TAG, "Wear APK dispatch failed", it) }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
