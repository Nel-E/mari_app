package com.mari.shared.data.serialization

object SchemaMigrations {

    fun migrate(file: TaskFile): TaskFile {
        var current = file
        while (current.schemaVersion < TaskFile.CURRENT_SCHEMA_VERSION) {
            current = when (current.schemaVersion) {
                1 -> migrateV1ToV2(current)
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
}
