package com.mari.shared.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object DueDateResolver {

    fun resolve(
        preset: DuePreset,
        zoneId: ZoneId = ZoneId.systemDefault(),
        specificDate: LocalDate? = null,
        specificTime: LocalTime? = null,
        month: Int? = null,
        year: Int? = null,
        now: Instant = Instant.now(),
    ): Pair<DueKind, Instant> {
        val today = LocalDateTime.ofInstant(now, zoneId).toLocalDate()
        val dueDate = when (preset) {
            DuePreset.SPECIFIC_DAY -> requireNotNull(specificDate) { "specificDate is required" }
            DuePreset.THIS_WEEK -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            DuePreset.NEXT_WEEK -> today.plusWeeks(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            DuePreset.THIS_MONTH -> today.with(TemporalAdjusters.lastDayOfMonth())
            DuePreset.NEXT_MONTH -> today.plusMonths(1).with(TemporalAdjusters.lastDayOfMonth())
            DuePreset.MONTH_YEAR -> {
                val dueMonth = requireNotNull(month) { "month is required" }
                val dueYear = requireNotNull(year) { "year is required" }
                LocalDate.of(dueYear, dueMonth, 1).with(TemporalAdjusters.lastDayOfMonth())
            }
        }
        val resolvedTime = specificTime ?: LocalTime.of(23, 59)
        val dueKind = DueKind(
            preset = preset,
            hasExplicitTime = specificTime != null,
            month = month,
            year = year,
        )
        return dueKind to dueDate.atTime(resolvedTime).atZone(zoneId).toInstant()
    }
}
