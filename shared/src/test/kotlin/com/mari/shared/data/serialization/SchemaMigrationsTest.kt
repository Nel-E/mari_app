package com.mari.shared.data.serialization

import com.google.common.truth.Truth.assertThat
import com.mari.shared.domain.DeviceId
import com.mari.shared.domain.FixedClock
import com.mari.shared.domain.Task
import com.mari.shared.domain.TaskPriority
import com.mari.shared.domain.TaskStatus
import org.junit.Test
import java.time.Instant

class SchemaMigrationsTest {

    private val clock = FixedClock(Instant.parse("2026-01-01T10:00:00Z"))

    private fun taskV1(id: String, name: String = "", description: String = ""): Task = Task(
        id = id,
        name = name,
        description = description,
        status = TaskStatus.TO_BE_DONE,
        createdAt = clock.nowUtc(),
        updatedAt = clock.nowUtc(),
        lastModifiedBy = DeviceId.PHONE,
    )

    @Test
    fun `v1 to v2 seeds name from description when name is blank`() {
        val file = TaskFile(
            schemaVersion = 1,
            tasks = listOf(taskV1("t1", name = "", description = "  My task desc  ")),
        )
        val migrated = SchemaMigrations.migrate(file)
        assertThat(migrated.tasks[0].name).isEqualTo("My task desc")
    }

    @Test
    fun `v1 to v2 falls back to Task id prefix when description is blank`() {
        val file = TaskFile(
            schemaVersion = 1,
            tasks = listOf(taskV1("abcdefghijk", name = "", description = "   ")),
        )
        val migrated = SchemaMigrations.migrate(file)
        assertThat(migrated.tasks[0].name).isEqualTo("Task abcdefgh")
    }

    @Test
    fun `v1 to v2 truncates seeded name to 80 characters`() {
        val longDesc = "A".repeat(100)
        val file = TaskFile(
            schemaVersion = 1,
            tasks = listOf(taskV1("t2", name = "", description = longDesc)),
        )
        val migrated = SchemaMigrations.migrate(file)
        assertThat(migrated.tasks[0].name.length).isEqualTo(80)
    }

    @Test
    fun `v1 to v2 preserves existing non-blank names`() {
        val file = TaskFile(
            schemaVersion = 1,
            tasks = listOf(taskV1("t3", name = "Existing name", description = "desc")),
        )
        val migrated = SchemaMigrations.migrate(file)
        assertThat(migrated.tasks[0].name).isEqualTo("Existing name")
    }

    @Test
    fun `v1 to v2 handles empty task list`() {
        val file = TaskFile(schemaVersion = 1, tasks = emptyList())
        val migrated = SchemaMigrations.migrate(file)
        assertThat(migrated.tasks).isEmpty()
        assertThat(migrated.schemaVersion).isEqualTo(TaskFile.CURRENT_SCHEMA_VERSION)
    }

    @Test
    fun `v2 to v3 converts queued tasks to very high priority to do tasks`() {
        @Suppress("DEPRECATION")
        val file = TaskFile(
            schemaVersion = 2,
            tasks = listOf(taskV1("t4", name = "Name").copy(status = TaskStatus.QUEUED)),
        )
        val migrated = SchemaMigrations.migrate(file)
        assertThat(migrated.schemaVersion).isEqualTo(TaskFile.CURRENT_SCHEMA_VERSION)
        assertThat(migrated.tasks.first().status).isEqualTo(TaskStatus.TO_BE_DONE)
        assertThat(migrated.tasks.first().priority).isEqualTo(TaskPriority.VERY_HIGH)
    }

    @Test
    fun `current file is returned unchanged`() {
        val file = TaskFile(schemaVersion = TaskFile.CURRENT_SCHEMA_VERSION, tasks = listOf(taskV1("t5", name = "Name")))
        val migrated = SchemaMigrations.migrate(file)
        assertThat(migrated).isSameInstanceAs(file)
    }

    @Test
    fun `unknown schema version above current throws`() {
        val file = TaskFile(schemaVersion = 999, tasks = emptyList())
        try {
            SchemaMigrations.migrate(file)
        } catch (e: IllegalStateException) {
            assertThat(e.message).contains("999")
            return
        }
    }
}
