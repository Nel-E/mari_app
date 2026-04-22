package com.mari.shared.domain

private const val SEED_NAME = "New Task"

object Seeding {

    fun ensureSeedTask(tasks: List<Task>, clock: Clock, deviceId: DeviceId): List<Task> {
        val hasVisible = tasks.any { it.deletedAt == null }
        return if (hasVisible) {
            tasks
        } else {
            tasks + ExecutionRules.createTask(
                name = SEED_NAME,
                clock = clock,
                deviceId = deviceId,
            )
        }
    }
}
