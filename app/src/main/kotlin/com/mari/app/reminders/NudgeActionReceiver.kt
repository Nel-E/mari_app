package com.mari.app.reminders

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import com.mari.shared.domain.TaskStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.mari.app.data.repository.FileTaskRepository
import com.mari.shared.domain.ExecutionRules
import com.mari.shared.domain.Clock
import javax.inject.Inject

@AndroidEntryPoint
class NudgeActionReceiver : BroadcastReceiver() {

    @Inject lateinit var taskRepository: FileTaskRepository
    @Inject lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent) {
        val nm = context.getSystemService<NotificationManager>()
        nm?.cancel(ReminderNotifier.DAILY_NUDGE_ID)

        when (intent.action) {
            ACTION_COMPLETE -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        taskRepository.update { tasks ->
                            tasks.map { t ->
                                if (t.id == taskId && t.status == TaskStatus.EXECUTING) {
                                    ExecutionRules.applyStatusChange(t, TaskStatus.COMPLETED, clock, com.mari.shared.domain.DeviceId.PHONE)
                                } else t
                            }
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
            ACTION_CHANGE -> {
                val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
                context.startActivity(launch)
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.mari.app.nudge.COMPLETE"
        const val ACTION_CHANGE = "com.mari.app.nudge.CHANGE"
        const val EXTRA_TASK_ID = "nudge_task_id"
    }
}
