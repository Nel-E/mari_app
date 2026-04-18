package com.mari.wear.data.cache

import com.mari.shared.data.serialization.TaskFile

interface TaskCacheStorage {
    fun exists(): Boolean
    fun load(): Result<TaskFile>
    fun save(taskFile: TaskFile): Result<Unit>
    fun initialFile(): TaskFile
}
