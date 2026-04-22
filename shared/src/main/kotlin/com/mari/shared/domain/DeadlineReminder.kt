package com.mari.shared.domain

import kotlinx.serialization.Serializable

@Serializable
data class DeadlineReminder(
    val offsetSeconds: Long,
    val label: String = "",
)
