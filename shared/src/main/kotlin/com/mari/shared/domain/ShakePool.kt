package com.mari.shared.domain

object ShakePool {

    private val ELIGIBLE_STATUSES = setOf(
        TaskStatus.TO_BE_DONE,
        TaskStatus.PAUSED,
        TaskStatus.QUEUED,
    )

    fun selectCandidates(tasks: List<Task>): List<Task> {
        val active = tasks.filter { it.deletedAt == null && it.status in ELIGIBLE_STATUSES }
        val queued = active.filter { it.status == TaskStatus.QUEUED }
        return if (queued.isNotEmpty()) queued else active
    }
}
