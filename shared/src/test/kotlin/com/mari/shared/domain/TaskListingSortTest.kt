package com.mari.shared.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class TaskListingSortTest {

    private val base = Instant.parse("2026-01-01T10:00:00Z")
    private val clock = FixedClock(base)

    private fun task(
        id: String,
        description: String = "Task $id",
        status: TaskStatus = TaskStatus.TO_BE_DONE,
        createdAt: Instant = base,
        updatedAt: Instant = base,
    ) = Task(
        id = id,
        description = description,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastModifiedBy = DeviceId.PHONE,
    )

    @Test
    fun `DEFAULT sort puts EXECUTING first then PAUSED then TO_BE_DONE`() {
        val tasks = listOf(
            task("a", status = TaskStatus.TO_BE_DONE),
            task("b", status = TaskStatus.PAUSED),
            task("c", status = TaskStatus.EXECUTING),
        )

        val sorted = TaskListing.sort(tasks, TaskListing.SortMode.DEFAULT)

        assertThat(sorted.map { it.status }).containsExactly(
            TaskStatus.EXECUTING,
            TaskStatus.PAUSED,
            TaskStatus.TO_BE_DONE,
        ).inOrder()
    }

    @Test
    fun `DEFAULT sort within same status orders by updatedAt descending`() {
        val older = base
        val newer = base.plusSeconds(60)
        val tasks = listOf(
            task("a", status = TaskStatus.TO_BE_DONE, updatedAt = older),
            task("b", status = TaskStatus.TO_BE_DONE, updatedAt = newer),
        )

        val sorted = TaskListing.sort(tasks, TaskListing.SortMode.DEFAULT)

        assertThat(sorted.map { it.id }).containsExactly("b", "a").inOrder()
    }

    @Test
    fun `DEFAULT sort puts COMPLETED before DISCARDED`() {
        val tasks = listOf(
            task("a", status = TaskStatus.DISCARDED),
            task("b", status = TaskStatus.COMPLETED),
        )

        val sorted = TaskListing.sort(tasks, TaskListing.SortMode.DEFAULT)

        assertThat(sorted.map { it.status }).containsExactly(
            TaskStatus.COMPLETED,
            TaskStatus.DISCARDED,
        ).inOrder()
    }

    @Test
    fun `A_Z sort orders by description case-insensitively ascending`() {
        val tasks = listOf(
            task("a", description = "Zebra"),
            task("b", description = "apple"),
            task("c", description = "Mango"),
        )

        val sorted = TaskListing.sort(tasks, TaskListing.SortMode.A_Z)

        assertThat(sorted.map { it.description }).containsExactly("apple", "Mango", "Zebra").inOrder()
    }

    @Test
    fun `Z_A sort orders by description case-insensitively descending`() {
        val tasks = listOf(
            task("a", description = "apple"),
            task("b", description = "Zebra"),
            task("c", description = "Mango"),
        )

        val sorted = TaskListing.sort(tasks, TaskListing.SortMode.Z_A)

        assertThat(sorted.map { it.description }).containsExactly("Zebra", "Mango", "apple").inOrder()
    }

    @Test
    fun `CREATED_NEWEST orders by createdAt descending`() {
        val t1 = base
        val t2 = base.plusSeconds(10)
        val t3 = base.plusSeconds(20)
        val tasks = listOf(
            task("a", createdAt = t1),
            task("b", createdAt = t3),
            task("c", createdAt = t2),
        )

        val sorted = TaskListing.sort(tasks, TaskListing.SortMode.CREATED_NEWEST)

        assertThat(sorted.map { it.id }).containsExactly("b", "c", "a").inOrder()
    }

    @Test
    fun `CREATED_OLDEST orders by createdAt ascending`() {
        val t1 = base
        val t2 = base.plusSeconds(10)
        val t3 = base.plusSeconds(20)
        val tasks = listOf(
            task("a", createdAt = t3),
            task("b", createdAt = t1),
            task("c", createdAt = t2),
        )

        val sorted = TaskListing.sort(tasks, TaskListing.SortMode.CREATED_OLDEST)

        assertThat(sorted.map { it.id }).containsExactly("b", "c", "a").inOrder()
    }

    @Test
    fun `MODIFIED_NEWEST orders by updatedAt descending`() {
        val t1 = base
        val t2 = base.plusSeconds(5)
        val tasks = listOf(
            task("a", updatedAt = t1),
            task("b", updatedAt = t2),
        )

        val sorted = TaskListing.sort(tasks, TaskListing.SortMode.MODIFIED_NEWEST)

        assertThat(sorted.map { it.id }).containsExactly("b", "a").inOrder()
    }

    @Test
    fun `MODIFIED_OLDEST orders by updatedAt ascending`() {
        val t1 = base
        val t2 = base.plusSeconds(5)
        val tasks = listOf(
            task("a", updatedAt = t2),
            task("b", updatedAt = t1),
        )

        val sorted = TaskListing.sort(tasks, TaskListing.SortMode.MODIFIED_OLDEST)

        assertThat(sorted.map { it.id }).containsExactly("b", "a").inOrder()
    }

    @Test
    fun `sort on empty list returns empty list`() {
        assertThat(TaskListing.sort(emptyList(), TaskListing.SortMode.DEFAULT)).isEmpty()
    }

    @Test
    fun `sort does not mutate input list`() {
        val original = listOf(
            task("a", status = TaskStatus.COMPLETED),
            task("b", status = TaskStatus.EXECUTING),
        )
        val copy = original.toList()

        TaskListing.sort(original, TaskListing.SortMode.DEFAULT)

        assertThat(original).isEqualTo(copy)
    }
}
