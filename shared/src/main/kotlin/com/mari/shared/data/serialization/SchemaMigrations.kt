package com.mari.shared.data.serialization

object SchemaMigrations {

    fun migrate(file: TaskFile): TaskFile {
        var current = file
        // Each branch migrates one version forward; add cases as schema evolves.
        while (current.schemaVersion < TaskFile.CURRENT_SCHEMA_VERSION) {
            current = when (current.schemaVersion) {
                // v1 is the first version — no migration needed yet.
                else -> throw IllegalStateException("No migration path from schema version ${current.schemaVersion}")
            }
        }
        return current
    }
}
