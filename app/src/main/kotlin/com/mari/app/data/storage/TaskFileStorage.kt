package com.mari.app.data.storage

import android.net.Uri
import com.mari.shared.data.serialization.TaskFile
import com.mari.shared.data.serialization.TaskFileCodec
import com.mari.shared.domain.DeviceId
import javax.inject.Inject

interface TaskStorage {
    fun load(treeUri: Uri): Result<TaskFile>
    fun save(treeUri: Uri, file: TaskFile): Result<Unit>
    fun exists(treeUri: Uri): Boolean
    fun initialFile(deviceId: DeviceId): TaskFile
}

class TaskFileStorage @Inject constructor(
    private val writer: AtomicWriter,
    private val rotator: WeeklyBackupRotator,
) : TaskStorage {
    override fun load(treeUri: Uri): Result<TaskFile> =
        writer.read(treeUri).fold(
            onSuccess = { raw ->
                TaskFileCodec.decode(raw).mapError { cause ->
                    StorageError.Corrupt(recovered = false, cause = cause)
                }
            },
            onFailure = { error ->
                when (error) {
                    is StorageError.Corrupt -> {
                        // Already attempted .bak recovery inside AtomicWriter.read; surface as-is
                        Result.failure(error)
                    }
                    else -> Result.failure(StorageError.Io(error))
                }
            },
        )

    override fun save(treeUri: Uri, file: TaskFile): Result<Unit> {
        val encoded = TaskFileCodec.encode(file)
        return writer.write(treeUri, encoded).also {
            if (it.isSuccess) {
                runCatching { rotator.runIfNeeded(treeUri, encoded) }
            }
        }
    }

    override fun exists(treeUri: Uri): Boolean = writer.exists(treeUri)

    override fun initialFile(deviceId: DeviceId): TaskFile =
        TaskFile(tasks = emptyList(), settings = com.mari.shared.data.serialization.FileSettings(deviceId.name.lowercase()))
}

private fun <T, R> Result<T>.mapError(transform: (Throwable) -> R): Result<T> where R : Throwable =
    if (isFailure) Result.failure(transform(exceptionOrNull()!!)) else this
