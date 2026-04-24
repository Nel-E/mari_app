package com.mari.shared.data.sync

import com.google.common.truth.Truth.assertThat
import com.mari.shared.domain.DeviceId
import com.mari.shared.domain.Task
import com.mari.shared.domain.TaskStatus
import org.junit.Test
import java.time.Instant

class SyncEngineTest {

    @Test
    fun `manifest sends tasks newer than remote tuples`() {
        val local = listOf(task(id = "a", version = 2), task(id = "b", version = 1))
        val remote = listOf(TaskTuple(id = "a", version = 1, updatedAt = Instant.EPOCH, lastModifiedBy = DeviceId.WATCH))

        val result = SyncEngine.planForManifest(local, remote)

        assertThat(result.map(Task::id)).containsExactly("a", "b")
    }

    @Test
    fun `delta applies incoming when local unchanged`() {
        val local = listOf(task(id = "a", version = 1))
        val incoming = listOf(task(id = "a", version = 2, modifiedBy = DeviceId.WATCH))

        val result = SyncEngine.planForDelta(local, incoming, mapOf("a" to 1))

        assertThat(result.toApplyLocal.map(Task::id)).containsExactly("a")
        assertThat(result.conflicts).isEmpty()
    }

    @Test
    fun `delta keeps local when local is newer`() {
        val local = listOf(task(id = "a", version = 3))
        val incoming = listOf(task(id = "a", version = 2, modifiedBy = DeviceId.WATCH))

        val result = SyncEngine.planForDelta(local, incoming, mapOf("a" to 2))

        assertThat(result.toSend.map(Task::id)).containsExactly("a")
        assertThat(result.toApplyLocal).isEmpty()
    }

    @Test
    fun `delta queues conflict on divergent edit`() {
        val local = listOf(task(id = "a", version = 3, status = TaskStatus.PAUSED))
        val incoming = listOf(task(id = "a", version = 4, status = TaskStatus.TO_BE_DONE, modifiedBy = DeviceId.WATCH))

        val result = SyncEngine.planForDelta(local, incoming, mapOf("a" to 2))

        assertThat(result.conflicts).hasSize(1)
    }

    private fun task(
        id: String,
        version: Int,
        status: TaskStatus = TaskStatus.TO_BE_DONE,
        modifiedBy: DeviceId = DeviceId.PHONE,
    ): Task =
        Task(
            id = id,
            description = "Task $id",
            status = status,
            createdAt = Instant.parse("2026-04-18T09:00:00Z"),
            updatedAt = Instant.parse("2026-04-18T10:00:00Z").plusSeconds(version.toLong()),
            executionStartedAt = null,
            deletedAt = null,
            version = version,
            lastModifiedBy = modifiedBy,
        )
}
