package com.mari.shared.data.serialization

import com.google.common.truth.Truth.assertThat
import com.mari.shared.domain.DeadlineReminder
import com.mari.shared.domain.DeviceId
import com.mari.shared.domain.DueKind
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

    @Test
    fun `DueKind SpecificDay round-trips via polymorphic serialization`() {
        val kind = DueKind.SpecificDay(dateIso = "2026-06-15", timeHhmm = "14:30")
        val task = makeTask("due1").copy(dueKind = kind)
        val file = TaskFile(tasks = listOf(task))
        val decoded = TaskFileCodec.decode(TaskFileCodec.encode(file))
        assertThat(decoded.getOrNull()?.tasks?.first()?.dueKind).isEqualTo(kind)
    }

    @Test
    fun `DueKind ThisWeek round-trips via polymorphic serialization`() {
        val task = makeTask("due2").copy(dueKind = DueKind.ThisWeek)
        val decoded = TaskFileCodec.decode(TaskFileCodec.encode(TaskFile(tasks = listOf(task))))
        assertThat(decoded.getOrNull()?.tasks?.first()?.dueKind).isEqualTo(DueKind.ThisWeek)
    }

    @Test
    fun `DueKind MonthYear round-trips via polymorphic serialization`() {
        val kind = DueKind.MonthYear(month = 3, year = 2027)
        val task = makeTask("due3").copy(dueKind = kind)
        val decoded = TaskFileCodec.decode(TaskFileCodec.encode(TaskFile(tasks = listOf(task))))
        assertThat(decoded.getOrNull()?.tasks?.first()?.dueKind).isEqualTo(kind)
    }

    @Test
    fun `encoded DueKind uses _type discriminator`() {
        val task = makeTask("due4").copy(dueKind = DueKind.ThisMonth)
        val json = TaskFileCodec.encode(TaskFile(tasks = listOf(task)))
        assertThat(json).contains("\"_type\"")
        assertThat(json).contains("this_month")
    }

    @Test
    fun `DeadlineReminder with null label round-trips`() {
        val reminder = DeadlineReminder(offsetSeconds = -3600L, label = null)
        val task = makeTask("r1").copy(deadlineReminders = listOf(reminder))
        val decoded = TaskFileCodec.decode(TaskFileCodec.encode(TaskFile(tasks = listOf(task))))
        assertThat(decoded.getOrNull()?.tasks?.first()?.deadlineReminders?.first()?.label).isNull()
    }
}
