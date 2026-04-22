package com.mari.shared.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DueDateResolverTest {

    private val zone = ZoneId.of("UTC")
    private val endOfDay = "23:59:59"

    private fun nowOn(date: String): Instant =
        LocalDate.parse(date).atStartOfDay(zone).toInstant()

    private fun resolvedDate(kind: DueKind, now: Instant): LocalDate =
        DueDateResolver.resolve(kind, now, zone).atZone(zone).toLocalDate()

    private fun resolvedTime(kind: DueKind, now: Instant): String =
        DueDateResolver.resolve(kind, now, zone).atZone(zone).toLocalTime().toString()

    @Test
    fun `ThisWeek resolves to same day when today is Sunday`() {
        val now = nowOn("2026-01-04") // Sunday
        val result = resolvedDate(DueKind.ThisWeek, now)
        assertThat(result.dayOfWeek).isEqualTo(DayOfWeek.SUNDAY)
        assertThat(result).isEqualTo(LocalDate.parse("2026-01-04"))
    }

    @Test
    fun `ThisWeek resolves to next Sunday when today is Monday`() {
        val now = nowOn("2026-01-05") // Monday
        val result = resolvedDate(DueKind.ThisWeek, now)
        assertThat(result.dayOfWeek).isEqualTo(DayOfWeek.SUNDAY)
        assertThat(result).isEqualTo(LocalDate.parse("2026-01-11"))
    }

    @Test
    fun `NextWeek resolves to Sunday of the following week`() {
        val now = nowOn("2026-01-05") // Monday
        val result = resolvedDate(DueKind.NextWeek, now)
        assertThat(result.dayOfWeek).isEqualTo(DayOfWeek.SUNDAY)
        assertThat(result).isEqualTo(LocalDate.parse("2026-01-18"))
    }

    @Test
    fun `ThisMonth resolves to last day of current month`() {
        val now = nowOn("2026-01-15")
        val result = resolvedDate(DueKind.ThisMonth, now)
        assertThat(result).isEqualTo(LocalDate.parse("2026-01-31"))
    }

    @Test
    fun `NextMonth resolves to last day of next month`() {
        val now = nowOn("2026-01-15")
        val result = resolvedDate(DueKind.NextMonth, now)
        assertThat(result).isEqualTo(LocalDate.parse("2026-02-28"))
    }

    @Test
    fun `MonthYear resolves to last day of specified month`() {
        val kind = DueKind.MonthYear(month = 2, year = 2024)
        val now = nowOn("2024-01-01")
        val result = resolvedDate(kind, now)
        assertThat(result).isEqualTo(LocalDate.parse("2024-02-29")) // 2024 is a leap year
    }

    @Test
    fun `SpecificDay without time resolves to end of day`() {
        val kind = DueKind.SpecificDay(dateIso = "2026-03-15")
        val now = nowOn("2026-01-01")
        assertThat(resolvedTime(kind, now)).isEqualTo(endOfDay)
        assertThat(resolvedDate(kind, now)).isEqualTo(LocalDate.parse("2026-03-15"))
    }

    @Test
    fun `SpecificDay with time resolves to given time`() {
        val kind = DueKind.SpecificDay(dateIso = "2026-03-15", timeHhmm = "09:30")
        val now = nowOn("2026-01-01")
        val resolved = DueDateResolver.resolve(kind, now, zone).atZone(zone)
        assertThat(resolved.toLocalDate()).isEqualTo(LocalDate.parse("2026-03-15"))
        assertThat(resolved.toLocalTime().hour).isEqualTo(9)
        assertThat(resolved.toLocalTime().minute).isEqualTo(30)
    }

    @Test
    fun `all simple kinds use end of day time`() {
        val now = nowOn("2026-01-05")
        listOf(DueKind.ThisWeek, DueKind.NextWeek, DueKind.ThisMonth, DueKind.NextMonth).forEach { kind ->
            assertThat(resolvedTime(kind, now)).isEqualTo(endOfDay)
        }
    }
}
