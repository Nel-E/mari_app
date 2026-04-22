package com.mari.shared.domain

import java.time.Instant

object TaskListing {

    enum class SortMode {
        DEFAULT,
        A_Z,
        Z_A,
        DUE_SOONEST,
        CREATED_NEWEST,
        CREATED_OLDEST,
        MODIFIED_NEWEST,
        MODIFIED_OLDEST,
    }

    fun sort(tasks: List<Task>, mode: SortMode): List<Task> = when (mode) {
        SortMode.DEFAULT -> tasks.sortedWith(
            compareBy<Task> { defaultGroupOrder(it.status) }
                .thenByDescending { it.updatedAt },
        )
        SortMode.A_Z -> tasks.sortedWith(compareBy<Task> { it.name.lowercase() }.thenBy { it.description.lowercase() })
        SortMode.Z_A -> tasks.sortedWith(compareByDescending<Task> { it.name.lowercase() }.thenByDescending { it.description.lowercase() })
        SortMode.DUE_SOONEST -> tasks.sortedWith(compareBy<Task> { it.dueAt ?: Instant.MAX }.thenBy { it.name.lowercase() })
        SortMode.CREATED_NEWEST -> tasks.sortedByDescending { it.createdAt }
        SortMode.CREATED_OLDEST -> tasks.sortedBy { it.createdAt }
        SortMode.MODIFIED_NEWEST -> tasks.sortedByDescending { it.updatedAt }
        SortMode.MODIFIED_OLDEST -> tasks.sortedBy { it.updatedAt }
    }

    fun filter(
        tasks: List<Task>,
        selectedStatuses: Set<TaskStatus>,
        query: String,
        includeDeleted: Boolean = false,
    ): List<Task> {
        val q = query.trim().lowercase()
        return tasks.filter { task ->
            if (!includeDeleted && task.deletedAt != null) return@filter false
            if (selectedStatuses.isNotEmpty() && task.status !in selectedStatuses) return@filter false
            if (
                q.isNotEmpty() &&
                !task.name.lowercase().contains(q) &&
                !task.description.lowercase().contains(q)
            ) return@filter false
            true
        }
    }

    private fun defaultGroupOrder(status: TaskStatus): Int = when (status) {
        TaskStatus.EXECUTING -> 0
        TaskStatus.QUEUED -> 1
        TaskStatus.PAUSED -> 2
        TaskStatus.TO_BE_DONE -> 3
        TaskStatus.COMPLETED -> 4
        TaskStatus.DISCARDED -> 5
    }
}
