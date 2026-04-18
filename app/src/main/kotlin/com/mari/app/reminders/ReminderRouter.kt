package com.mari.app.reminders

import com.mari.app.di.AlarmScheduler
import com.mari.app.di.WorkScheduler
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderRouter @Inject constructor(
    @AlarmScheduler private val alarmScheduler: ReminderScheduler,
    @WorkScheduler private val workManagerScheduler: ReminderScheduler,
) : ReminderScheduler {

    override fun schedule(taskId: String, intervalMs: Long, taskDescription: String) {
        if (intervalMs < WORK_MANAGER_THRESHOLD_MS) {
            alarmScheduler.schedule(taskId, intervalMs, taskDescription)
        } else {
            workManagerScheduler.schedule(taskId, intervalMs, taskDescription)
        }
    }

    override fun cancel(taskId: String) {
        alarmScheduler.cancel(taskId)
        workManagerScheduler.cancel(taskId)
    }

    companion object {
        private const val WORK_MANAGER_THRESHOLD_MS = 15 * 60 * 1000L
    }
}
