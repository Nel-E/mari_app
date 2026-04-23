package com.mari.app.appupdate

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.mari.app.data.remote.AppUpdateApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: AppUpdateApiService,
) {
    suspend fun downloadAndVerify(
        track: String,
        component: String,
        fileName: String,
        expectedSha256: String,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): Result<File> = runCatching {
        val response = api.downloadArtifact(track, component, fileName)
        val body = response.body() ?: error("Empty response body (HTTP ${response.code()})")

        val cacheDir = File(context.cacheDir, "app_updates").also { it.mkdirs() }
        val outFile = File(cacheDir, fileName)

        val digest = MessageDigest.getInstance("SHA-256")
        val totalBytes = body.contentLength()

        body.byteStream().use { input ->
            outFile.outputStream().use { output ->
                val buffer = ByteArray(8 * 1024)
                var downloaded = 0L
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    downloaded += read
                    onProgress(downloaded, totalBytes)
                }
            }
        }

        val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actualSha.equals(expectedSha256, ignoreCase = true)) {
            outFile.delete()
            error("SHA-256 mismatch: expected $expectedSha256, got $actualSha")
        }

        outFile
    }

    fun canRequestInstallPackages(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun buildInstallIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildInstallPermissionSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
