package com.mari.shared.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TaskStatus {
    @SerialName("to_be_done") TO_BE_DONE,
    @SerialName("paused") PAUSED,
    @SerialName("executing") EXECUTING,
    @Deprecated("Queued is retained only for decoding older task files. Use TaskPriority instead.")
    @SerialName("queued") QUEUED,
    @SerialName("completed") COMPLETED,
    @SerialName("discarded") DISCARDED,
}
