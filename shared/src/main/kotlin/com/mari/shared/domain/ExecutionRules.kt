package com.mari.shared.domain

import java.util.UUID

object ExecutionRules {

    fun canSetExecuting(tasks: List<Task>, excludeId: String? = null): Boolean =
        tasks.none { it.status == TaskStatus.EXECUTING && it.id != excludeId && it.deletedAt == null }

    fun currentlyExecuting(tasks: List<Task>): Task? =
        tasks.firstOrNull { it.status == TaskStatus.EXECUTING && it.deletedAt == null }

    fun applyStatusChange(
        task: Task,
        newStatus: TaskStatus,
        clock: Clock,
        deviceId: DeviceId,
    ): Task {
        val now = clock.nowUtc()
        val executionStartedAt = when {
            newStatus == TaskStatus.EXECUTING -> now
            task.status == TaskStatus.EXECUTING -> null
            else -> task.executionStartedAt
        }
        return task.copy(
            status = newStatus,
            executionStartedAt = executionStartedAt,
            updatedAt = now,
            version = task.version + 1,
            lastModifiedBy = deviceId,
        )
    }

    fun createTask(
        description: String,
        clock: Clock,
        deviceId: DeviceId,
        id: String = UUID.randomUUID().toString(),
    ): Task {
        val now = clock.nowUtc()
        return Task(
            id = id,
            description = description,
            status = TaskStatus.TO_BE_DONE,
            createdAt = now,
            updatedAt = now,
            lastModifiedBy = deviceId,
        )
    }

    fun softDelete(task: Task, clock: Clock, deviceId: DeviceId): Task {
        val now = clock.nowUtc()
        return task.copy(
            deletedAt = now,
            executionStartedAt = if (task.status == TaskStatus.EXECUTING) null else task.executionStartedAt,
            status = if (task.status == TaskStatus.EXECUTING) TaskStatus.DISCARDED else task.status,
            updatedAt = now,
            version = task.version + 1,
            lastModifiedBy = deviceId,
        )
    }
}
