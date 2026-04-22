package com.mari.shared.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object DueDateResolver {

    fun resolve(kind: DueKind, now: Instant, zone: ZoneId = ZoneId.systemDefault()): Instant {
        val today = LocalDateTime.ofInstant(now, zone).toLocalDate()
        val endOfDay = LocalTime.of(23, 59, 59)
        return when (kind) {
            is DueKind.SpecificDay -> {
                val date = LocalDate.parse(kind.dateIso)
                val time = kind.timeHhmm?.let { LocalTime.parse(it) } ?: endOfDay
                date.atTime(time).atZone(zone).toInstant()
            }
            DueKind.ThisWeek ->
                today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(endOfDay).atZone(zone).toInstant()
            DueKind.NextWeek ->
                today.plusWeeks(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(endOfDay).atZone(zone).toInstant()
            DueKind.ThisMonth ->
                today.with(TemporalAdjusters.lastDayOfMonth()).atTime(endOfDay).atZone(zone).toInstant()
            DueKind.NextMonth ->
                today.plusMonths(1).with(TemporalAdjusters.lastDayOfMonth()).atTime(endOfDay).atZone(zone).toInstant()
            is DueKind.MonthYear ->
                LocalDate.of(kind.year, kind.month, 1).with(TemporalAdjusters.lastDayOfMonth()).atTime(endOfDay).atZone(zone).toInstant()
        }
    }
}
