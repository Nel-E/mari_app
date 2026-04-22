package com.mari.app.reminders

import com.mari.app.data.storage.SafGrant
import com.mari.app.data.storage.SafSource
import com.mari.app.data.storage.TaskStorage
import com.mari.shared.domain.TaskStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BootRescheduler @Inject constructor(
    private val safSource: SafSource,
    private val storage: TaskStorage,
    private val reminderScheduler: ReminderScheduler,
    private val deadlineReminderScheduler: DeadlineReminderScheduler,
) {

    suspend fun rescheduleAll() {
        safSource.init()
        val grant = safSource.grant.value
        if (grant !is SafGrant.Granted || !storage.exists(grant.treeUri)) return
        val tasks = storage.load(grant.treeUri).getOrNull()?.tasks.orEmpty()
        val executing = tasks.firstOrNull { it.status == TaskStatus.EXECUTING && it.deletedAt == null }
        if (executing != null) {
            reminderScheduler.schedule(executing.id, DEFAULT_INTERVAL_MS, executing.name)
        }
        tasks.filter { task ->
            task.deletedAt == null &&
                task.status != TaskStatus.COMPLETED &&
                task.dueAt != null &&
                task.deadlineReminders.isNotEmpty()
        }.forEach(deadlineReminderScheduler::schedule)
    }

    private companion object {
        const val DEFAULT_INTERVAL_MS = 30 * 60 * 1000L
    }
}
