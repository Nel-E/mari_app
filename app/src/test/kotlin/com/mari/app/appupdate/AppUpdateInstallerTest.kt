package com.mari.app.appupdate

import com.google.common.truth.Truth.assertThat
import com.mari.app.data.repository.FakeAppUpdateApiService
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Response
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUpdateInstallerTest {

    private val ctx get() = RuntimeEnvironment.getApplication()

    @Test
    fun `SHA match - returns downloaded file`() = runTest {
        val content = "fake apk content".toByteArray()
        val expectedSha = sha256Hex(content)
        val fakeApi = FakeAppUpdateApiService().apply {
            downloadResponse = Response.success(
                content.toResponseBody("application/octet-stream".toMediaType()),
            )
        }

        val installer = AppUpdateInstaller(ctx, fakeApi)
        val result = installer.downloadAndVerify("release", "phone", "mari-2.apk", expectedSha)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()?.name).isEqualTo("mari-2.apk")
        assertThat(result.getOrNull()?.exists()).isTrue()
    }

    @Test
    fun `SHA mismatch - deletes file and returns failure`() = runTest {
        val content = "fake apk content".toByteArray()
        val wrongSha = "0000000000000000000000000000000000000000000000000000000000000000"
        val fakeApi = FakeAppUpdateApiService().apply {
            downloadResponse = Response.success(
                content.toResponseBody("application/octet-stream".toMediaType()),
            )
        }

        val installer = AppUpdateInstaller(ctx, fakeApi)
        val result = installer.downloadAndVerify("release", "phone", "mari-2.apk", wrongSha)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("SHA-256 mismatch")
        // File must be deleted after mismatch
        val cacheFile = java.io.File(ctx.cacheDir, "app_updates/mari-2.apk")
        assertThat(cacheFile.exists()).isFalse()
    }

    @Test
    fun `HTTP error response - returns failure`() = runTest {
        val fakeApi = FakeAppUpdateApiService().apply {
            downloadResponse = Response.error(
                404,
                "not found".toResponseBody("text/plain".toMediaType()),
            )
        }

        val installer = AppUpdateInstaller(ctx, fakeApi)
        val result = installer.downloadAndVerify("release", "phone", "missing.apk", "anyhash")

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `progress callback receives bytes`() = runTest {
        val content = ByteArray(16 * 1024) { it.toByte() }
        val expectedSha = sha256Hex(content)
        val fakeApi = FakeAppUpdateApiService().apply {
            downloadResponse = Response.success(
                content.toResponseBody("application/octet-stream".toMediaType()),
            )
        }

        val progressUpdates = mutableListOf<Long>()
        val installer = AppUpdateInstaller(ctx, fakeApi)
        installer.downloadAndVerify("release", "phone", "mari-progress.apk", expectedSha) { downloaded, _ ->
            progressUpdates.add(downloaded)
        }

        assertThat(progressUpdates).isNotEmpty()
        assertThat(progressUpdates.last()).isEqualTo(content.size.toLong())
    }

    // ---------------------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------------------

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
