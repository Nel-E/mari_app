package com.mari.app.reminders

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerReminderScheduler @Inject constructor(
    private val workManager: WorkManager,
) : ReminderScheduler {

    override fun schedule(taskId: String, intervalMs: Long, taskDescription: String) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(intervalMs, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    ReminderWorker.KEY_TASK_ID to taskId,
                    ReminderWorker.KEY_DESCRIPTION to taskDescription,
                ),
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            workName(taskId),
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request,
        )
    }

    override fun cancel(taskId: String) {
        workManager.cancelUniqueWork(workName(taskId))
    }

    private fun workName(taskId: String) = "reminder_$taskId"
}
