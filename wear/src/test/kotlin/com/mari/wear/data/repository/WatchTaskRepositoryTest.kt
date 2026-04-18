package com.mari.wear.data.repository

import com.google.common.truth.Truth.assertThat
import com.mari.shared.data.serialization.FileSettings
import com.mari.shared.data.serialization.TaskFile
import com.mari.shared.domain.DeviceId
import com.mari.shared.domain.FixedClock
import com.mari.shared.domain.Task
import com.mari.shared.domain.TaskStatus
import com.mari.wear.data.cache.TaskCacheStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

class WatchTaskRepositoryTest {

    private lateinit var storage: FakeTaskCacheStorage
    private lateinit var repository: WatchTaskRepository

    @Before
    fun setUp() {
        storage = FakeTaskCacheStorage()
        repository = WatchTaskRepository(storage)
    }

    @Test
    fun `init with no existing file seeds and emits tasks`() = runTest {
        storage.exists = false
        repository.init()

        val tasks = repository.observeTasks().first()
        assertThat(tasks).isNotEmpty()
        assertThat(storage.saved).isNotNull()
    }

    @Test
    fun `init with existing file loads tasks`() = runTest {
        val task = testTask("1")
        storage.exists = true
        storage.stored = TaskFile(tasks = listOf(task), settings = testSettings())

        repository.init()

        val tasks = repository.observeTasks().first()
        assertThat(tasks.map { it.id }).contains("1")
    }

    @Test
    fun `update appends task and persists`() = runTest {
        storage.exists = true
        storage.stored = TaskFile(tasks = emptyList(), settings = testSettings())
        repository.init()

        val newTask = testTask("42")
        repository.update { tasks -> tasks + newTask }

        val tasks = repository.observeTasks().first()
        assertThat(tasks.map { it.id }).contains("42")
        assertThat(storage.saved?.tasks?.any { it.id == "42" }).isTrue()
    }

    @Test
    fun `update propagates storage failure without mutating in-memory state`() = runTest {
        storage.exists = true
        storage.stored = TaskFile(tasks = emptyList(), settings = testSettings())
        repository.init()
        storage.failOnSave = true

        val result = repository.update { tasks -> tasks + testTask("99") }

        assertThat(result.isFailure).isTrue()
        val tasks = repository.observeTasks().first()
        assertThat(tasks.none { it.id == "99" }).isTrue()
    }

    @Test
    fun `getTasks returns current snapshot`() = runTest {
        storage.exists = true
        storage.stored = TaskFile(tasks = listOf(testTask("a")), settings = testSettings())
        repository.init()

        val tasks = repository.getTasks()
        assertThat(tasks.map { it.id }).contains("a")
    }

    private fun testTask(id: String): Task {
        val clock = FixedClock(Instant.parse("2026-01-01T10:00:00Z"))
        return Task(
            id = id,
            description = "Task $id",
            status = TaskStatus.TO_BE_DONE,
            createdAt = clock.nowUtc(),
            updatedAt = clock.nowUtc(),
            deletedAt = null,
            executionStartedAt = null,
            version = 1,
            lastModifiedBy = DeviceId.WATCH,
        )
    }

    private fun testSettings() = FileSettings(deviceId = "watch")
}

private class FakeTaskCacheStorage : TaskCacheStorage {
    var exists = false
    var stored: TaskFile? = null
    var saved: TaskFile? = null
    var failOnSave = false

    override fun exists(): Boolean = exists

    override fun load(): Result<TaskFile> =
        stored?.let { Result.success(it) } ?: Result.failure(IllegalStateException("no file"))

    override fun save(taskFile: TaskFile): Result<Unit> {
        if (failOnSave) return Result.failure(IllegalStateException("disk full"))
        saved = taskFile
        stored = taskFile
        return Result.success(Unit)
    }

    override fun initialFile(): TaskFile =
        TaskFile(tasks = emptyList(), settings = FileSettings(deviceId = "watch"))
}
