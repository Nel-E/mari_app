package com.mari.shared.data.serialization

import com.mari.shared.domain.DueKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

object TaskFileCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
        coerceInputValues = false
        classDiscriminator = "_type"
        serializersModule = SerializersModule {
            polymorphic(DueKind::class) {
                subclass(DueKind.SpecificDay::class)
                subclass(DueKind.ThisWeek::class)
                subclass(DueKind.NextWeek::class)
                subclass(DueKind.ThisMonth::class)
                subclass(DueKind.NextMonth::class)
                subclass(DueKind.MonthYear::class)
            }
        }
    }

    fun encode(file: TaskFile): String = json.encodeToString(TaskFile.serializer(), file)

    fun decode(raw: String): Result<TaskFile> = runCatching {
        val parsed = json.decodeFromString(TaskFile.serializer(), raw)
        SchemaMigrations.migrate(parsed)
    }
}
