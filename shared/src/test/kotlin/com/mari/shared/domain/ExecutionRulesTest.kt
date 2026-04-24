package com.mari.shared.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class ExecutionRulesTest {

    private val clock = FixedClock(Instant.parse("2026-01-01T10:00:00Z"))
    private val device = DeviceId.PHONE

    private fun task(
        id: String = "1",
        status: TaskStatus = TaskStatus.TO_BE_DONE,
        deletedAt: Instant? = null,
        version: Int = 1,
        executionStartedAt: Instant? = null,
    ) = Task(
        id = id,
        description = "Test",
        status = status,
        createdAt = clock.nowUtc(),
        updatedAt = clock.nowUtc(),
        deletedAt = deletedAt,
        executionStartedAt = executionStartedAt,
        version = version,
        lastModifiedBy = device,
    )

    @Test
    fun `canSetExecuting returns true when no task is executing`() {
        val tasks = listOf(task(status = TaskStatus.TO_BE_DONE), task(id = "2", status = TaskStatus.PAUSED))
        assertThat(ExecutionRules.canSetExecuting(tasks)).isTrue()
    }

    @Test
    fun `canSetExecuting returns false when a task is executing`() {
        val tasks = listOf(task(status = TaskStatus.EXECUTING))
        assertThat(ExecutionRules.canSetExecuting(tasks)).isFalse()
    }

    @Test
    fun `canSetExecuting ignores excluded id`() {
        val tasks = listOf(task(id = "exec", status = TaskStatus.EXECUTING))
        assertThat(ExecutionRules.canSetExecuting(tasks, excludeId = "exec")).isTrue()
    }

    @Test
    fun `canSetExecuting ignores soft-deleted executing tasks`() {
        val tasks = listOf(task(status = TaskStatus.EXECUTING, deletedAt = clock.nowUtc()))
        assertThat(ExecutionRules.canSetExecuting(tasks)).isTrue()
    }

    @Test
    fun `applyStatusChange to EXECUTING sets executionStartedAt`() {
        val t = task(status = TaskStatus.TO_BE_DONE)
        val result = ExecutionRules.applyStatusChange(t, TaskStatus.EXECUTING, clock, device)
        assertThat(result.status).isEqualTo(TaskStatus.EXECUTING)
        assertThat(result.executionStartedAt).isEqualTo(clock.nowUtc())
    }

    @Test
    fun `applyStatusChange leaving EXECUTING clears executionStartedAt`() {
        val started = Instant.parse("2026-01-01T09:00:00Z")
        val t = task(status = TaskStatus.EXECUTING, executionStartedAt = started)

        for (newStatus in listOf(TaskStatus.COMPLETED, TaskStatus.PAUSED, TaskStatus.TO_BE_DONE, TaskStatus.DISCARDED)) {
            val result = ExecutionRules.applyStatusChange(t, newStatus, clock, device)
            assertThat(result.executionStartedAt).isNull()
        }
    }

    @Test
    fun `applyStatusChange between non-executing statuses preserves executionStartedAt`() {
        val t = task(status = TaskStatus.TO_BE_DONE)
        val result = ExecutionRules.applyStatusChange(t, TaskStatus.PAUSED, clock, device)
        assertThat(result.executionStartedAt).isNull()
    }

    @Test
    fun `applyStatusChange increments version`() {
        val t = task(version = 3)
        val result = ExecutionRules.applyStatusChange(t, TaskStatus.PAUSED, clock, device)
        assertThat(result.version).isEqualTo(4)
    }

    @Test
    fun `applyStatusChange updates updatedAt`() {
        val earlier = Instant.parse("2025-01-01T00:00:00Z")
        val t = task().copy(updatedAt = earlier)
        val result = ExecutionRules.applyStatusChange(t, TaskStatus.PAUSED, clock, device)
        assertThat(result.updatedAt).isEqualTo(clock.nowUtc())
    }

    @Test
    fun `createTask uses TO_BE_DONE status`() {
        val t = ExecutionRules.createTask("Buy milk", clock, device)
        assertThat(t.status).isEqualTo(TaskStatus.TO_BE_DONE)
        assertThat(t.name).isEqualTo("Buy milk")
        assertThat(t.version).isEqualTo(1)
        assertThat(t.deletedAt).isNull()
        assertThat(t.executionStartedAt).isNull()
    }

    @Test
    fun `softDelete sets deletedAt and increments version`() {
        val t = task(status = TaskStatus.TO_BE_DONE)
        val result = ExecutionRules.softDelete(t, clock, device)
        assertThat(result.deletedAt).isEqualTo(clock.nowUtc())
        assertThat(result.version).isEqualTo(2)
    }

    @Test
    fun `softDelete on executing task clears executionStartedAt and sets DISCARDED`() {
        val started = Instant.parse("2026-01-01T09:00:00Z")
        val t = task(status = TaskStatus.EXECUTING, executionStartedAt = started)
        val result = ExecutionRules.softDelete(t, clock, device)
        assertThat(result.executionStartedAt).isNull()
        assertThat(result.status).isEqualTo(TaskStatus.DISCARDED)
    }
}
