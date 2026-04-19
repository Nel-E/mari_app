package com.mari.app.wearinstall

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
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
) {
    private val dataClient by lazy { Wearable.getDataClient(context) }

    suspend fun dispatchIfAvailable() {
        val apkBytes = try {
            context.assets.open(APK_ASSET_NAME).use { it.readBytes() }
        } catch (e: Exception) {
            Log.d(TAG, "Wear APK asset not found — skipping dispatch")
            return
        }

        val sha256 = sha256Hex(apkBytes)
        val request = PutDataMapRequest.create(DATA_PATH).apply {
            dataMap.putByteArray("apk", apkBytes)
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
