package com.mari.shared.data.serialization

import com.mari.shared.domain.Task
import kotlinx.serialization.Serializable

@Serializable
data class TaskFile(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val tasks: List<Task>,
    val settings: FileSettings = FileSettings(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
    }
}

@Serializable
data class FileSettings(
    val deviceId: String = "phone",
)
