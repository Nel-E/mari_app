package com.mari.wear.wearinstall

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

private const val TAG = "WearApkInstallerService"

class WearApkInstallerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            val item = event.dataItem
            if (!item.uri.path.orEmpty().startsWith("/mari/wear/apk")) return@forEach
            scope.launch { handleApkDelivery(DataMapItem.fromDataItem(item)) }
        }
    }

    private suspend fun handleApkDelivery(dataMapItem: DataMapItem) {
        val expectedSha = dataMapItem.dataMap.getString("sha256") ?: return
        val asset = dataMapItem.dataMap.getAsset("apk") ?: return

        val incomingVersionCode = dataMapItem.dataMap.getInt("version_code", 0)
        if (incomingVersionCode > 0) {
            val installedVersionCode = runCatching {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionCode
            }.getOrDefault(0)
            if (incomingVersionCode <= installedVersionCode) {
                Log.d(TAG, "Wear APK versionCode $incomingVersionCode already installed ($installedVersionCode) — skipping")
                return
            }
        }

        val apkBytes = runCatching {
            Wearable.getDataClient(this)
                .getFdForAsset(asset)
                .await()
                .inputStream
                .use { it.readBytes() }
        }.getOrElse { e ->
            Log.e(TAG, "Failed to read APK asset", e)
            return
        }

        val actualSha = sha256Hex(apkBytes)
        if (actualSha != expectedSha) {
            Log.w(TAG, "APK checksum mismatch — aborting install")
            return
        }

        Log.i(TAG, "Received wear APK ($actualSha) — installing")
        installApk(apkBytes)
    }

    private fun installApk(apkBytes: ByteArray) {
        val installer = packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }

        val sessionId = runCatching { installer.createSession(params) }.getOrElse { e ->
            Log.e(TAG, "Failed to create install session", e)
            return
        }

        val session = runCatching { installer.openSession(sessionId) }.getOrElse { e ->
            Log.e(TAG, "Failed to open install session", e)
            installer.abandonSession(sessionId)
            return
        }

        runCatching {
            session.openWrite("mari-wear.apk", 0, apkBytes.size.toLong()).use { output ->
                output.write(apkBytes)
                session.fsync(output)
            }
            val intent = Intent("com.mari.wear.INSTALL_COMPLETE")
            val pi = PendingIntent.getBroadcast(
                this,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pi.intentSender)
        }.onFailure { e ->
            Log.e(TAG, "APK install session failed", e)
        }

        session.close()
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
