package com.mari.wear.data.repository

import com.mari.shared.data.repository.TaskRepository
import com.mari.shared.data.serialization.FileSettings
import com.mari.shared.data.serialization.TaskFile
import com.mari.shared.domain.DeviceId
import com.mari.shared.domain.Seeding
import com.mari.shared.domain.SystemClock
import com.mari.shared.domain.Task
import com.mari.wear.data.cache.TaskCacheStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchTaskRepository @Inject constructor(
    private val storage: TaskCacheStorage,
) : TaskRepository {

    private val mutex = Mutex()
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())

    override fun observeTasks(): Flow<List<Task>> = _tasks.asStateFlow()

    override suspend fun getTasks(): List<Task> = _tasks.value

    override suspend fun update(transform: (List<Task>) -> List<Task>): Result<Unit> =
        mutex.withLock {
            val newTasks = transform(_tasks.value)
            val seeded = Seeding.ensureSeedTask(newTasks, SystemClock, DeviceId.WATCH)
            val current = storage.load()
                .getOrElse { TaskFile(tasks = seeded, settings = FileSettings(deviceId = "watch")) }
                .copy(tasks = seeded)
            storage.save(current).also { result ->
                if (result.isSuccess) _tasks.value = seeded
            }
        }

    suspend fun init() = mutex.withLock {
        if (!storage.exists()) {
            val initial = storage.initialFile()
            val seeded = Seeding.ensureSeedTask(initial.tasks, SystemClock, DeviceId.WATCH)
            storage.save(initial.copy(tasks = seeded))
            _tasks.value = seeded
            return
        }
        storage.load().onSuccess { file ->
            val seeded = Seeding.ensureSeedTask(file.tasks, SystemClock, DeviceId.WATCH)
            _tasks.value = seeded
        }
    }
}
