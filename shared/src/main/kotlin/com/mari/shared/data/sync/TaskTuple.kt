package com.mari.shared.data.sync

import com.mari.shared.domain.DeviceId
import com.mari.shared.domain.InstantSerializer
import com.mari.shared.domain.Task
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class TaskTuple(
    val id: String,
    val version: Int,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant,
    val lastModifiedBy: DeviceId,
) {
    companion object {
        fun from(task: Task): TaskTuple =
            TaskTuple(
                id = task.id,
                version = task.version,
                updatedAt = task.updatedAt,
                lastModifiedBy = task.lastModifiedBy,
            )
    }
}
