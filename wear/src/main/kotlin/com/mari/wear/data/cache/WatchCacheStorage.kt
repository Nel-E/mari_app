package com.mari.wear.data.cache

import android.content.Context
import com.mari.shared.data.serialization.FileSettings
import com.mari.shared.data.serialization.TaskFile
import com.mari.shared.data.serialization.TaskFileCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchCacheStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) : TaskCacheStorage {
    private val file = File(context.filesDir, CACHE_FILE_NAME)
    private val tempFile = File(context.filesDir, "$CACHE_FILE_NAME.tmp")

    override fun exists(): Boolean = file.exists()

    override fun load(): Result<TaskFile> = runCatching {
        TaskFileCodec.decode(file.readText()).getOrThrow()
    }

    override fun save(taskFile: TaskFile): Result<Unit> = runCatching {
        tempFile.writeText(TaskFileCodec.encode(taskFile))
        if (!tempFile.renameTo(file)) {
            file.writeText(TaskFileCodec.encode(taskFile))
        }
    }

    override fun initialFile(): TaskFile = TaskFile(
        tasks = emptyList(),
        settings = FileSettings(deviceId = "watch"),
    )

    companion object {
        private const val CACHE_FILE_NAME = "tasks.json"
    }
}
