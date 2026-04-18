package com.mari.shared.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class TaskListingFilterTest {

    private val base = Instant.parse("2026-01-01T10:00:00Z")

    private fun task(
        id: String,
        description: String = "Task $id",
        status: TaskStatus = TaskStatus.TO_BE_DONE,
        deletedAt: Instant? = null,
    ) = Task(
        id = id,
        description = description,
        status = status,
        createdAt = base,
        updatedAt = base,
        deletedAt = deletedAt,
        lastModifiedBy = DeviceId.PHONE,
    )

    @Test
    fun `no filters returns all non-deleted tasks`() {
        val tasks = listOf(
            task("a"),
            task("b"),
            task("c", deletedAt = base),
        )

        val result = TaskListing.filter(tasks, emptySet(), "")

        assertThat(result.map { it.id }).containsExactly("a", "b")
    }

    @Test
    fun `includeDeleted true returns deleted tasks too`() {
        val tasks = listOf(
            task("a"),
            task("b", deletedAt = base),
        )

        val result = TaskListing.filter(tasks, emptySet(), "", includeDeleted = true)

        assertThat(result.map { it.id }).containsExactly("a", "b")
    }

    @Test
    fun `status filter returns only matching tasks`() {
        val tasks = listOf(
            task("a", status = TaskStatus.TO_BE_DONE),
            task("b", status = TaskStatus.PAUSED),
            task("c", status = TaskStatus.EXECUTING),
        )

        val result = TaskListing.filter(tasks, setOf(TaskStatus.PAUSED, TaskStatus.EXECUTING), "")

        assertThat(result.map { it.id }).containsExactly("b", "c")
    }

    @Test
    fun `empty status set does not filter by status`() {
        val tasks = listOf(
            task("a", status = TaskStatus.TO_BE_DONE),
            task("b", status = TaskStatus.COMPLETED),
        )

        val result = TaskListing.filter(tasks, emptySet(), "")

        assertThat(result).hasSize(2)
    }

    @Test
    fun `query filters by description substring case-insensitively`() {
        val tasks = listOf(
            task("a", description = "Buy groceries"),
            task("b", description = "Read a book"),
            task("c", description = "GROCERIES run"),
        )

        val result = TaskListing.filter(tasks, emptySet(), "groceries")

        assertThat(result.map { it.id }).containsExactly("a", "c")
    }

    @Test
    fun `query with leading and trailing whitespace is trimmed`() {
        val tasks = listOf(
            task("a", description = "Buy milk"),
            task("b", description = "Buy bread"),
        )

        val result = TaskListing.filter(tasks, emptySet(), "  milk  ")

        assertThat(result.map { it.id }).containsExactly("a")
    }

    @Test
    fun `blank query does not filter by description`() {
        val tasks = listOf(task("a"), task("b"))

        val result = TaskListing.filter(tasks, emptySet(), "   ")

        assertThat(result).hasSize(2)
    }

    @Test
    fun `status filter and query are combined with AND logic`() {
        val tasks = listOf(
            task("a", description = "Walk dog", status = TaskStatus.TO_BE_DONE),
            task("b", description = "Walk cat", status = TaskStatus.PAUSED),
            task("c", description = "Run", status = TaskStatus.TO_BE_DONE),
        )

        val result = TaskListing.filter(tasks, setOf(TaskStatus.TO_BE_DONE), "walk")

        assertThat(result.map { it.id }).containsExactly("a")
    }

    @Test
    fun `deleted tasks excluded by status filter even when includeDeleted is false`() {
        val tasks = listOf(
            task("a", status = TaskStatus.DISCARDED, deletedAt = base),
            task("b", status = TaskStatus.DISCARDED),
        )

        val result = TaskListing.filter(tasks, setOf(TaskStatus.DISCARDED), "")

        assertThat(result.map { it.id }).containsExactly("b")
    }

    @Test
    fun `filter on empty list returns empty list`() {
        val result = TaskListing.filter(emptyList(), emptySet(), "anything")

        assertThat(result).isEmpty()
    }

    @Test
    fun `no match returns empty list`() {
        val tasks = listOf(task("a", description = "Buy milk"))

        val result = TaskListing.filter(tasks, emptySet(), "xyz")

        assertThat(result).isEmpty()
    }
}
