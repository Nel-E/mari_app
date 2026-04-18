package com.mari.shared.domain

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class Task(
    val id: String,
    val description: String,
    val status: TaskStatus,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant,
    @Serializable(with = InstantSerializer::class)
    val executionStartedAt: Instant? = null,
    @Serializable(with = InstantSerializer::class)
    val deletedAt: Instant? = null,
    val version: Int = 1,
    val lastModifiedBy: DeviceId,
)
