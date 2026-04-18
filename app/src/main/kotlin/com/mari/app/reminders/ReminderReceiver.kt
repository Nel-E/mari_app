package com.mari.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notifier: ReminderNotifier

    @Inject
    lateinit var alarmScheduler: AlarmReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val description = intent.getStringExtra(EXTRA_DESCRIPTION) ?: return
        val intervalMs = intent.getLongExtra(EXTRA_INTERVAL_MS, -1L)
        if (intervalMs <= 0L) return

        notifier.notify(taskId, description)
        alarmScheduler.schedule(taskId, intervalMs, description)
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_DESCRIPTION = "task_description"
        const val EXTRA_INTERVAL_MS = "interval_ms"
    }
}
