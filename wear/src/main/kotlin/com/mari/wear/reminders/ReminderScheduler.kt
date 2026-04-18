package com.mari.wear.reminders

interface ReminderScheduler {
    fun schedule(taskId: String, intervalMs: Long, taskDescription: String)
    fun cancel(taskId: String)
}
