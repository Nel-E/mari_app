package com.mari.shared.data.serialization

import com.google.common.truth.Truth.assertThat
import com.mari.shared.domain.DeviceId
import com.mari.shared.domain.FixedClock
import com.mari.shared.domain.Task
import com.mari.shared.domain.TaskStatus
import org.junit.Test
import java.time.Instant

class TaskFileCodecTest {

    private val clock = FixedClock(Instant.parse("2026-01-01T10:00:00Z"))

    private fun makeTask(id: String) = Task(
        id = id,
        description = "Task $id",
        status = TaskStatus.TO_BE_DONE,
        createdAt = clock.nowUtc(),
        updatedAt = clock.nowUtc(),
        lastModifiedBy = DeviceId.PHONE,
    )

    @Test
    fun `encode then decode round-trips correctly`() {
        val file = TaskFile(tasks = listOf(makeTask("1"), makeTask("2")))
        val json = TaskFileCodec.encode(file)
        val decoded = TaskFileCodec.decode(json)
        assertThat(decoded.isSuccess).isTrue()
        assertThat(decoded.getOrNull()).isEqualTo(file)
    }

    @Test
    fun `decode tolerates unknown fields`() {
        val json = """
            {
              "schemaVersion": 1,
              "tasks": [],
              "settings": {"deviceId": "phone"},
              "unknownField": "ignored"
            }
        """.trimIndent()
        val result = TaskFileCodec.decode(json)
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()?.tasks).isEmpty()
    }

    @Test
    fun `decode returns failure on malformed JSON`() {
        val result = TaskFileCodec.decode("{ not valid json {{")
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `decode returns failure on empty string`() {
        val result = TaskFileCodec.decode("")
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `encode includes schemaVersion`() {
        val file = TaskFile(tasks = emptyList())
        val json = TaskFileCodec.encode(file)
        assertThat(json).contains("\"schemaVersion\"")
        assertThat(json).doesNotContain("\"schemaVersion\":\"1\"")
        assertThat(json).contains("\"schemaVersion\":")
    }

    @Test
    fun `task with null optional fields round-trips`() {
        val task = makeTask("x").copy(executionStartedAt = null, deletedAt = null)
        val file = TaskFile(tasks = listOf(task))
        val decoded = TaskFileCodec.decode(TaskFileCodec.encode(file))
        assertThat(decoded.getOrNull()?.tasks?.first()?.executionStartedAt).isNull()
        assertThat(decoded.getOrNull()?.tasks?.first()?.deletedAt).isNull()
    }
}
