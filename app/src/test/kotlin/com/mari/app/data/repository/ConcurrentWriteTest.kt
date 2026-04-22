package com.mari.app.data.repository

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.mari.app.data.storage.SafGrant
import com.mari.app.data.storage.SafSource
import com.mari.app.data.storage.StorageError
import com.mari.app.data.storage.TaskStorage
import com.mari.shared.data.serialization.FileSettings
import com.mari.shared.data.serialization.TaskFile
import com.mari.shared.domain.DeviceId
import com.mari.shared.domain.ExecutionRules
import com.mari.shared.domain.FixedClock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConcurrentWriteTest {

    private val treeUri: Uri = Uri.parse("content://test/tree")
    private val clock = FixedClock(Instant.parse("2026-04-18T10:00:00Z"))

    private fun repo(safSource: SafSource, storage: TaskStorage) =
        FileTaskRepository(safSource, storage)

    @Test
    fun `100 concurrent updates are all applied exactly once`() = runTest {
        val storage = InMemoryTaskStorage()
        val saf = FakeSafSource(treeUri)
        val repository = repo(saf, storage)
        repository.onGrantAcquired()

        val results = (1..100).map { i ->
            async {
                val task = ExecutionRules.createTask("Task $i", clock, DeviceId.PHONE, id = "id-$i")
                repository.update { current -> current + task }
            }
        }.awaitAll()

        assertThat(results.all { it.isSuccess }).isTrue()
        val ourTasks = repository.getTasks().filter { it.id.startsWith("id-") }
        assertThat(ourTasks).hasSize(100)
        assertThat(ourTasks.map { it.id }.toSet()).hasSize(100)
    }

    @Test
    fun `update returns NoGrant failure when SAF grant is missing`() = runTest {
        val storage = InMemoryTaskStorage()
        val saf = FakeSafSource(treeUri, grantMissing = true)
        val repository = repo(saf, storage)

        val result = repository.update { it }

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(StorageError.NoGrant::class.java)
    }

    @Test
    fun `failed storage write does not update in-memory state`() = runTest {
        val storage = InMemoryTaskStorage(failWrite = true)
        val saf = FakeSafSource(treeUri)
        val repository = repo(saf, storage)
        repository.onGrantAcquired()

        val task = ExecutionRules.createTask("Task A", clock, DeviceId.PHONE, id = "id-a")
        val result = repository.update { current -> current + task }

        assertThat(result.isFailure).isTrue()
        assertThat(repository.getTasks().none { it.id == "id-a" }).isTrue()
    }
}

private class FakeSafSource(
    treeUri: Uri,
    grantMissing: Boolean = false,
) : SafSource {
    override val grant: StateFlow<SafGrant> = MutableStateFlow(
        if (grantMissing) SafGrant.Missing else SafGrant.Granted(treeUri),
    )

    override suspend fun init() = Unit
}

private class InMemoryTaskStorage(
    private val failWrite: Boolean = false,
) : TaskStorage {

    private var stored = TaskFile(tasks = emptyList(), settings = FileSettings("phone"))

    override fun load(treeUri: Uri): Result<TaskFile> = Result.success(stored)

    override fun save(treeUri: Uri, file: TaskFile): Result<Unit> {
        if (failWrite) return Result.failure(StorageError.Io(RuntimeException("disk full")))
        stored = file
        return Result.success(Unit)
    }

    override fun exists(treeUri: Uri): Boolean = true

    override fun initialFile(deviceId: DeviceId): TaskFile =
        TaskFile(tasks = emptyList(), settings = FileSettings(deviceId.name.lowercase()))
}
