package com.mari.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.mari.app.settings.SettingsRepository

@AndroidEntryPoint
class DeadlineReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notifier: ReminderNotifier

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val taskName = intent.getStringExtra(EXTRA_TASK_NAME) ?: return
        val dueAtMillis = intent.getLongExtra(EXTRA_DUE_AT, -1L)
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        if (dueAtMillis <= 0L) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = settingsRepository.current()
                notifier.notifyDeadline(
                    taskId = taskId,
                    title = label.ifBlank { "Task due soon" },
                    taskName = taskName,
                    dueAt = Instant.ofEpochMilli(dueAtMillis),
                    quietWindow = settings.quietWindow,
                )
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "deadline_task_id"
        const val EXTRA_TASK_NAME = "deadline_task_name"
        const val EXTRA_DUE_AT = "deadline_due_at"
        const val EXTRA_OFFSET_SECONDS = "deadline_offset_seconds"
        const val EXTRA_LABEL = "deadline_label"
    }
}
