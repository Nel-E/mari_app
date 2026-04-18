package com.mari.shared.data.sync

import com.google.common.truth.Truth.assertThat
import com.mari.shared.domain.DeviceId
import com.mari.shared.domain.Task
import com.mari.shared.domain.TaskStatus
import org.junit.Test
import java.time.Instant

class ConflictClassifierTest {

    @Test
    fun `same version is no-op`() {
        val task = task(version = 2)
        assertThat(ConflictClassifier.classify(task, task, 1)).isEqualTo(ConflictDecision.NO_OP)
    }

    @Test
    fun `incoming newer while local unchanged adopts incoming`() {
        val local = task(version = 1, updatedAt = Instant.parse("2026-04-18T10:00:00Z"))
        val remote = local.copy(version = 2, updatedAt = Instant.parse("2026-04-18T11:00:00Z"))
        assertThat(ConflictClassifier.classify(local, remote, 1)).isEqualTo(ConflictDecision.ADOPT_INCOMING)
    }

    @Test
    fun `local newer while remote unchanged adopts local`() {
        val local = task(version = 3)
        val remote = task(version = 2)
        assertThat(ConflictClassifier.classify(local, remote, 2)).isEqualTo(ConflictDecision.ADOPT_LOCAL)
    }

    @Test
    fun `delete versus edit becomes merge-delete-edit`() {
        val local = task(version = 3, deletedAt = Instant.parse("2026-04-18T10:30:00Z"))
        val remote = task(version = 4, description = "Edited remotely")
        assertThat(ConflictClassifier.classify(local, remote, 2)).isEqualTo(ConflictDecision.MERGE_DELETE_EDIT)
    }

    @Test
    fun `same status with newer updatedAt adopts newer`() {
        val local = task(version = 3, status = TaskStatus.PAUSED, updatedAt = Instant.parse("2026-04-18T10:00:00Z"))
        val remote = task(version = 4, status = TaskStatus.PAUSED, updatedAt = Instant.parse("2026-04-18T11:00:00Z"))
        assertThat(ConflictClassifier.classify(local, remote, 2)).isEqualTo(ConflictDecision.ADOPT_INCOMING)
    }

    @Test
    fun `different statuses become conflict`() {
        val local = task(version = 3, status = TaskStatus.PAUSED)
        val remote = task(version = 4, status = TaskStatus.QUEUED)
        assertThat(ConflictClassifier.classify(local, remote, 2)).isEqualTo(ConflictDecision.CONFLICT)
    }

    private fun task(
        version: Int,
        description: String = "Task",
        status: TaskStatus = TaskStatus.TO_BE_DONE,
        updatedAt: Instant = Instant.parse("2026-04-18T10:00:00Z"),
        deletedAt: Instant? = null,
    ): Task =
        Task(
            id = "task-1",
            description = description,
            status = status,
            createdAt = Instant.parse("2026-04-18T09:00:00Z"),
            updatedAt = updatedAt,
            executionStartedAt = null,
            deletedAt = deletedAt,
            version = version,
            lastModifiedBy = DeviceId.PHONE,
        )
}
