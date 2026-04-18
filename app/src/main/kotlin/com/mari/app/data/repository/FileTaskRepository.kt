package com.mari.app.data.repository

import com.mari.app.data.storage.SafGrant
import com.mari.app.data.storage.SafSource
import com.mari.app.data.storage.StorageError
import com.mari.app.data.storage.TaskStorage
import com.mari.shared.data.repository.TaskRepository
import com.mari.shared.data.serialization.TaskFile
import com.mari.shared.domain.DeviceId
import com.mari.shared.domain.Seeding
import com.mari.shared.domain.SystemClock
import com.mari.shared.domain.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileTaskRepository @Inject constructor(
    private val safManager: SafSource,
    private val storage: TaskStorage,
) : TaskRepository {

    private val mutex = Mutex()
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())

    override fun observeTasks(): Flow<List<Task>> = _tasks.asStateFlow()

    override suspend fun getTasks(): List<Task> = _tasks.value

    override suspend fun update(transform: (List<Task>) -> List<Task>): Result<Unit> =
        mutex.withLock {
            val grant = safManager.grant.value
            if (grant !is SafGrant.Granted) return Result.failure(StorageError.NoGrant)

            val newTasks = transform(_tasks.value)
            val seeded = Seeding.ensureSeedTask(newTasks, SystemClock, DeviceId.PHONE)
            val current = storage.load(grant.treeUri)
                .getOrElse { TaskFile(tasks = seeded) }
                .copy(tasks = seeded)

            storage.save(grant.treeUri, current).also { result ->
                if (result.isSuccess) _tasks.value = seeded
            }
        }

    suspend fun init() {
        safManager.init()
        val grant = safManager.grant.value
        if (grant is SafGrant.Granted) {
            loadFromDisk(grant)
        }
    }

    suspend fun onGrantAcquired() {
        val grant = safManager.grant.value
        if (grant is SafGrant.Granted) loadFromDisk(grant)
    }

    private suspend fun loadFromDisk(grant: SafGrant.Granted) {
        mutex.withLock {
            if (!storage.exists(grant.treeUri)) {
                val initial = storage.initialFile(DeviceId.PHONE)
                val seeded = Seeding.ensureSeedTask(initial.tasks, SystemClock, DeviceId.PHONE)
                storage.save(grant.treeUri, initial.copy(tasks = seeded))
                _tasks.value = seeded
                return@withLock
            }

            storage.load(grant.treeUri)
                .onSuccess { file ->
                    val seeded = Seeding.ensureSeedTask(file.tasks, SystemClock, DeviceId.PHONE)
                    _tasks.value = seeded
                }
                .onFailure { error ->
                    // Surface error to UI via a dedicated error state in the future;
                    // keep in-memory state unchanged so app remains usable read-only.
                    _storageError.value = error as? StorageError
                }
        }
    }

    private val _storageError = MutableStateFlow<StorageError?>(null)
    val storageError: Flow<StorageError?> = _storageError.asStateFlow()
}
