package com.mari.shared.data.serialization

import com.mari.shared.domain.TaskPriority
import com.mari.shared.domain.TaskStatus

object SchemaMigrations {

    fun migrate(file: TaskFile): TaskFile {
        var current = file
        while (current.schemaVersion < TaskFile.CURRENT_SCHEMA_VERSION) {
            current = when (current.schemaVersion) {
                1 -> migrateV1ToV2(current)
                2 -> migrateV2ToV3(current)
                else -> throw IllegalStateException("No migration path from schema version ${current.schemaVersion}")
            }
        }
        return current
    }

    private fun migrateV1ToV2(file: TaskFile): TaskFile = file.copy(
        schemaVersion = 2,
        tasks = file.tasks.map { task ->
            task.copy(
                name = task.name.ifBlank {
                    task.description.trim().ifBlank { "Task ${task.id.take(8)}" }.take(80)
                },
            )
        },
    )

    private fun migrateV2ToV3(file: TaskFile): TaskFile = file.copy(
        schemaVersion = 3,
        tasks = file.tasks.map { task ->
            @Suppress("DEPRECATION")
            if (task.status == TaskStatus.QUEUED) {
                task.copy(
                    status = TaskStatus.TO_BE_DONE,
                    priority = TaskPriority.VERY_HIGH,
                )
            } else {
                task
            }
        },
    )
}
