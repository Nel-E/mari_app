package com.mari.shared.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface DueKind {
    @Serializable @SerialName("specific_day")
    data class SpecificDay(val dateIso: String, val timeHhmm: String? = null) : DueKind

    @Serializable @SerialName("this_week")
    data object ThisWeek : DueKind

    @Serializable @SerialName("next_week")
    data object NextWeek : DueKind

    @Serializable @SerialName("this_month")
    data object ThisMonth : DueKind

    @Serializable @SerialName("next_month")
    data object NextMonth : DueKind

    @Serializable @SerialName("month_year")
    data class MonthYear(val month: Int, val year: Int) : DueKind
}

enum class DuePreset {
    SPECIFIC_DAY,
    THIS_WEEK,
    NEXT_WEEK,
    THIS_MONTH,
    NEXT_MONTH,
    MONTH_YEAR,
}

val DueKind.preset: DuePreset
    get() = when (this) {
        is DueKind.SpecificDay -> DuePreset.SPECIFIC_DAY
        DueKind.ThisWeek -> DuePreset.THIS_WEEK
        DueKind.NextWeek -> DuePreset.NEXT_WEEK
        DueKind.ThisMonth -> DuePreset.THIS_MONTH
        DueKind.NextMonth -> DuePreset.NEXT_MONTH
        is DueKind.MonthYear -> DuePreset.MONTH_YEAR
    }

fun DuePreset.toSimpleDueKind(): DueKind? = when (this) {
    DuePreset.THIS_WEEK -> DueKind.ThisWeek
    DuePreset.NEXT_WEEK -> DueKind.NextWeek
    DuePreset.THIS_MONTH -> DueKind.ThisMonth
    DuePreset.NEXT_MONTH -> DueKind.NextMonth
    else -> null
}
