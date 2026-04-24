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
        return touchTask(
            task.copy(
                status = newStatus,
                executionStartedAt = executionStartedAt,
            ),
            clock,
            deviceId,
        )
    }

    fun updateTaskMetadata(
        task: Task,
        clock: Clock,
        deviceId: DeviceId,
        name: String,
        description: String = task.description,
        dueAt: java.time.Instant? = task.dueAt,
        dueKind: DueKind? = task.dueKind,
        deadlineReminders: List<DeadlineReminder> = task.deadlineReminders,
        priority: TaskPriority = task.priority,
        colorHex: String? = task.colorHex,
        customColorHex: String? = task.customColorHex,
        useCustomColor: Boolean = task.useCustomColor,
    ): Task = touchTask(
        task.copy(
            name = name,
            description = description,
            dueAt = dueAt,
            dueKind = dueKind,
            deadlineReminders = deadlineReminders,
            priority = priority,
            colorHex = colorHex,
            customColorHex = customColorHex,
            useCustomColor = useCustomColor,
        ),
        clock,
        deviceId,
    )

    fun createTask(
        name: String,
        clock: Clock,
        deviceId: DeviceId,
        description: String = "",
        dueAt: java.time.Instant? = null,
        dueKind: DueKind? = null,
        deadlineReminders: List<DeadlineReminder> = emptyList(),
        priority: TaskPriority = TaskPriority.NORMAL,
        colorHex: String? = null,
        customColorHex: String? = null,
        useCustomColor: Boolean = false,
        id: String = UUID.randomUUID().toString(),
    ): Task {
        val now = clock.nowUtc()
        return Task(
            id = id,
            name = name,
            description = description,
            status = TaskStatus.TO_BE_DONE,
            createdAt = now,
            updatedAt = now,
            dueAt = dueAt,
            dueKind = dueKind,
            deadlineReminders = deadlineReminders,
            priority = priority,
            colorHex = colorHex,
            customColorHex = customColorHex,
            useCustomColor = useCustomColor,
            lastModifiedBy = deviceId,
        )
    }

    fun softDelete(task: Task, clock: Clock, deviceId: DeviceId): Task {
        val deletedTask = if (task.status == TaskStatus.EXECUTING) {
            task.copy(
                deletedAt = clock.nowUtc(),
                executionStartedAt = null,
                status = TaskStatus.DISCARDED,
            )
        } else {
            task.copy(deletedAt = clock.nowUtc())
        }
        return touchTask(deletedTask, clock, deviceId)
    }

    private fun touchTask(
        task: Task,
        clock: Clock,
        deviceId: DeviceId,
    ): Task {
        val now = clock.nowUtc()
        return task.copy(
            updatedAt = now,
            version = task.version + 1,
            lastModifiedBy = deviceId,
        )
    }
}
