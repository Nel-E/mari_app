package com.mari.app.reminders

interface ReminderScheduler {
    fun schedule(taskId: String, intervalMs: Long, taskDescription: String)
    fun cancel(taskId: String)
}
