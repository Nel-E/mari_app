package com.mari.shared.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class SeedingTest {

    private val clock = FixedClock(Instant.parse("2026-01-01T10:00:00Z"))
    private val device = DeviceId.PHONE

    private fun task(id: String, deletedAt: Instant? = null) = Task(
        id = id,
        description = "Task $id",
        status = TaskStatus.TO_BE_DONE,
        createdAt = clock.nowUtc(),
        updatedAt = clock.nowUtc(),
        deletedAt = deletedAt,
        lastModifiedBy = device,
    )

    @Test
    fun `empty list gets one seed task`() {
        val result = Seeding.ensureSeedTask(emptyList(), clock, device)
        assertThat(result).hasSize(1)
        assertThat(result[0].name).isEqualTo("New Task")
        assertThat(result[0].status).isEqualTo(TaskStatus.TO_BE_DONE)
    }

    @Test
    fun `all soft-deleted list gets one seed task appended`() {
        val tasks = listOf(task("1", deletedAt = clock.nowUtc()), task("2", deletedAt = clock.nowUtc()))
        val result = Seeding.ensureSeedTask(tasks, clock, device)
        assertThat(result).hasSize(3)
        assertThat(result.last().name).isEqualTo("New Task")
    }

    @Test
    fun `non-empty list is unchanged`() {
        val tasks = listOf(task("1"))
        val result = Seeding.ensureSeedTask(tasks, clock, device)
        assertThat(result).isEqualTo(tasks)
    }

    @Test
    fun `mixed deleted and non-deleted list is unchanged`() {
        val tasks = listOf(task("1", deletedAt = clock.nowUtc()), task("2"))
        val result = Seeding.ensureSeedTask(tasks, clock, device)
        assertThat(result).isEqualTo(tasks)
    }

    @Test
    fun `seed task lastModifiedBy matches device`() {
        val result = Seeding.ensureSeedTask(emptyList(), clock, DeviceId.WATCH)
        assertThat(result[0].lastModifiedBy).isEqualTo(DeviceId.WATCH)
    }
}
