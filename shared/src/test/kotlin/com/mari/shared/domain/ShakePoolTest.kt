package com.mari.shared.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class ShakePoolTest {

    private val now = Instant.parse("2026-01-01T10:00:00Z")

    private fun task(
        id: String,
        status: TaskStatus,
        deletedAt: Instant? = null,
    ) = Task(
        id = id,
        description = "Task $id",
        status = status,
        createdAt = now,
        updatedAt = now,
        deletedAt = deletedAt,
        lastModifiedBy = DeviceId.PHONE,
    )

    @Test
    fun `empty list returns empty candidates`() {
        assertThat(ShakePool.selectCandidates(emptyList())).isEmpty()
    }

    @Test
    fun `returns only queued tasks when any exist`() {
        val tasks = listOf(
            task("1", TaskStatus.TO_BE_DONE),
            task("2", TaskStatus.QUEUED),
            task("3", TaskStatus.PAUSED),
            task("4", TaskStatus.QUEUED),
        )
        val result = ShakePool.selectCandidates(tasks)
        assertThat(result.map { it.id }).containsExactly("2", "4")
    }

    @Test
    fun `returns TO_BE_DONE and PAUSED when no queued tasks`() {
        val tasks = listOf(
            task("1", TaskStatus.TO_BE_DONE),
            task("2", TaskStatus.PAUSED),
            task("3", TaskStatus.COMPLETED),
            task("4", TaskStatus.DISCARDED),
        )
        val result = ShakePool.selectCandidates(tasks)
        assertThat(result.map { it.id }).containsExactly("1", "2")
    }

    @Test
    fun `excludes soft-deleted tasks`() {
        val tasks = listOf(
            task("1", TaskStatus.TO_BE_DONE, deletedAt = now),
            task("2", TaskStatus.TO_BE_DONE),
        )
        val result = ShakePool.selectCandidates(tasks)
        assertThat(result.map { it.id }).containsExactly("2")
    }

    @Test
    fun `excludes COMPLETED and DISCARDED tasks`() {
        val tasks = listOf(
            task("1", TaskStatus.COMPLETED),
            task("2", TaskStatus.DISCARDED),
            task("3", TaskStatus.EXECUTING),
        )
        assertThat(ShakePool.selectCandidates(tasks)).isEmpty()
    }

    @Test
    fun `excludes EXECUTING tasks from shake pool`() {
        val tasks = listOf(
            task("1", TaskStatus.EXECUTING),
            task("2", TaskStatus.TO_BE_DONE),
        )
        val result = ShakePool.selectCandidates(tasks)
        assertThat(result.map { it.id }).containsExactly("2")
    }
}
