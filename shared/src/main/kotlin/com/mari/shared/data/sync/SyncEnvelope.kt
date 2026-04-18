package com.mari.shared.data.sync

import com.mari.shared.domain.InstantSerializer
import com.mari.shared.domain.Task
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import java.time.Instant

@OptIn(ExperimentalSerializationApi::class)
private val syncCbor = Cbor {
    ignoreUnknownKeys = true
}

@Serializable
sealed interface SyncEnvelope {
    val schemaVersion: Int
    @Serializable(with = InstantSerializer::class)
    val sentAtUtc: Instant

    @Serializable
    @SerialName("tuple_manifest")
    data class TupleManifest(
        override val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
        @Serializable(with = InstantSerializer::class)
        override val sentAtUtc: Instant,
        val deviceId: String,
        val tuples: List<TaskTuple>,
    ) : SyncEnvelope

    @Serializable
    @SerialName("delta_bundle")
    data class DeltaBundle(
        override val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
        @Serializable(with = InstantSerializer::class)
        override val sentAtUtc: Instant,
        val deviceId: String,
        val fromVersionByTask: Map<String, Int>,
        val changedTasks: List<Task>,
    ) : SyncEnvelope

    @Serializable
    @SerialName("ack")
    data class Ack(
        override val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
        @Serializable(with = InstantSerializer::class)
        override val sentAtUtc: Instant,
        val deviceId: String,
        val upToVersion: Map<String, Int>,
        val conflicts: List<String>,
    ) : SyncEnvelope

    @OptIn(ExperimentalSerializationApi::class)
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        fun encode(envelope: SyncEnvelope): ByteArray =
            syncCbor.encodeToByteArray(SyncEnvelope.serializer(), envelope)

        fun decode(bytes: ByteArray): SyncEnvelope =
            syncCbor.decodeFromByteArray(SyncEnvelope.serializer(), bytes)
    }
}
