package com.mari.shared.domain

object ShakePool {

    private val ELIGIBLE_STATUSES = setOf(
        TaskStatus.TO_BE_DONE,
        TaskStatus.PAUSED,
    )

    fun selectCandidates(tasks: List<Task>): List<Task> {
        val active = tasks.filter { it.deletedAt == null && it.status in ELIGIBLE_STATUSES }
        val veryHigh = active.filter { it.priority == TaskPriority.VERY_HIGH }
        if (veryHigh.isNotEmpty()) return veryHigh
        val high = active.filter { it.priority == TaskPriority.HIGH }
        return if (high.isNotEmpty()) high else active
    }
}
