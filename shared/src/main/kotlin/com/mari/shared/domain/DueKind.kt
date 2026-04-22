package com.mari.shared.domain

import kotlinx.serialization.Serializable

@Serializable
data class DueKind(
    val preset: DuePreset,
    val hasExplicitTime: Boolean = false,
    val month: Int? = null,
    val year: Int? = null,
)

@Serializable
enum class DuePreset {
    SPECIFIC_DAY,
    THIS_WEEK,
    NEXT_WEEK,
    THIS_MONTH,
    NEXT_MONTH,
    MONTH_YEAR,
}
