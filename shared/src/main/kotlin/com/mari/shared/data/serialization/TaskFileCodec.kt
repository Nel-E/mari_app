package com.mari.shared.data.serialization

import kotlinx.serialization.json.Json

object TaskFileCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
        coerceInputValues = false
    }

    fun encode(file: TaskFile): String = json.encodeToString(TaskFile.serializer(), file)

    fun decode(raw: String): Result<TaskFile> = runCatching {
        val parsed = json.decodeFromString(TaskFile.serializer(), raw)
        SchemaMigrations.migrate(parsed)
    }
}
