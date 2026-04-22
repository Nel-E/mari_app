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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class CrashDuringWriteTest {

    private val treeUri: Uri = Uri.parse("content://test/tree")
    private val clock = FixedClock(Instant.parse("2026-04-18T10:00:00Z"))

    private fun repo(safSource: SafSource, storage: TaskStorage) =
        FileTaskRepository(safSource, storage)

    @Test
    fun `corrupt canonical file is surfaced as storageError and leaves tasks empty`() = runTest {
        val storage = CorruptOnFirstLoadStorage()
        val saf = GrantedSafSource(treeUri)
        val repository = repo(saf, storage)

        repository.onGrantAcquired()

        assertThat(repository.getTasks()).isEmpty()
        assertThat(repository.storageError.first()).isInstanceOf(StorageError.Corrupt::class.java)
    }

    @Test
    fun `update succeeds after corrupt load sets tasks correctly`() = runTest {
        val storage = CorruptOnFirstLoadStorage()
        val saf = GrantedSafSource(treeUri)
        val repository = repo(saf, storage)
        repository.onGrantAcquired() // loads corrupt → storageError set, tasks empty

        val task = ExecutionRules.createTask("Recovery task", clock, DeviceId.PHONE, "rec-1")
        val result = repository.update { current -> current + task }

        assertThat(result.isSuccess).isTrue()
        val tasks = repository.getTasks().filter { it.id == "rec-1" }
        assertThat(tasks).hasSize(1)
    }

    @Test
    fun `write failure after corrupt load does not update tasks`() = runTest {
        val storage = CorruptThenFailWriteStorage()
        val saf = GrantedSafSource(treeUri)
        val repository = repo(saf, storage)
        repository.onGrantAcquired()

        val task = ExecutionRules.createTask("Should not persist", clock, DeviceId.PHONE, "fail-1")
        val result = repository.update { current -> current + task }

        assertThat(result.isFailure).isTrue()
        assertThat(repository.getTasks().none { it.id == "fail-1" }).isTrue()
    }
}

private class GrantedSafSource(treeUri: Uri) : SafSource {
    override val grant: StateFlow<SafGrant> =
        MutableStateFlow(SafGrant.Granted(treeUri))

    override suspend fun init() = Unit
}

private class CorruptOnFirstLoadStorage : TaskStorage {
    private var firstLoad = true
    private var stored = TaskFile(tasks = emptyList(), settings = FileSettings("phone"))

    override fun load(treeUri: Uri): Result<TaskFile> {
        if (firstLoad) {
            firstLoad = false
            return Result.failure(StorageError.Corrupt(recovered = false))
        }
        return Result.success(stored)
    }

    override fun save(treeUri: Uri, file: TaskFile): Result<Unit> {
        stored = file
        return Result.success(Unit)
    }

    override fun exists(treeUri: Uri): Boolean = true

    override fun initialFile(deviceId: DeviceId): TaskFile =
        TaskFile(tasks = emptyList(), settings = FileSettings(deviceId.name.lowercase()))
}

private class CorruptThenFailWriteStorage : TaskStorage {
    private var firstLoad = true

    override fun load(treeUri: Uri): Result<TaskFile> {
        if (firstLoad) {
            firstLoad = false
            return Result.failure(StorageError.Corrupt(recovered = false))
        }
        return Result.failure(StorageError.Corrupt(recovered = false))
    }

    override fun save(treeUri: Uri, file: TaskFile): Result<Unit> =
        Result.failure(StorageError.Io(RuntimeException("write failed")))

    override fun exists(treeUri: Uri): Boolean = true

    override fun initialFile(deviceId: DeviceId): TaskFile =
        TaskFile(tasks = emptyList(), settings = FileSettings(deviceId.name.lowercase()))
}
