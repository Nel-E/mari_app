package com.mari.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import com.mari.shared.domain.Task
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

interface DeadlineReminderScheduler {
    fun schedule(task: Task)
    fun cancel(taskId: String)
}

@Singleton
class AlarmDeadlineReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmManager: AlarmManager,
) : DeadlineReminderScheduler {

    private val prefs = context.getSharedPreferences("deadline_alarm_registry", Context.MODE_PRIVATE)

    override fun schedule(task: Task) {
        cancel(task.id)
        val dueAt = task.dueAt ?: return
        val offsets = mutableListOf<Long>()
        task.deadlineReminders.forEach { reminder ->
            val triggerAtMillis = dueAt.plusSeconds(reminder.offsetSeconds).toEpochMilli()
            if (triggerAtMillis <= System.currentTimeMillis()) return@forEach
            offsets += reminder.offsetSeconds
            val pendingIntent = buildPendingIntent(
                taskId = task.id,
                taskName = task.name,
                dueAt = dueAt,
                offsetSeconds = reminder.offsetSeconds,
                label = reminder.label,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }
        prefs.edit().putString(task.id, offsets.joinToString(",")).apply()
    }

    override fun cancel(taskId: String) {
        loadOffsets(taskId).forEach { offsetSeconds ->
            val pendingIntent = buildCancelIntent(taskId, offsetSeconds)
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        prefs.edit().remove(taskId).apply()
    }

    private fun buildPendingIntent(
        taskId: String,
        taskName: String,
        dueAt: Instant,
        offsetSeconds: Long,
        label: String?,
    ): PendingIntent {
        val intent = Intent(context, DeadlineReminderReceiver::class.java).apply {
            putExtra(DeadlineReminderReceiver.EXTRA_TASK_ID, taskId)
            putExtra(DeadlineReminderReceiver.EXTRA_TASK_NAME, taskName)
            putExtra(DeadlineReminderReceiver.EXTRA_DUE_AT, dueAt.toEpochMilli())
            putExtra(DeadlineReminderReceiver.EXTRA_OFFSET_SECONDS, offsetSeconds)
            putExtra(DeadlineReminderReceiver.EXTRA_LABEL, label)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(taskId, offsetSeconds),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildCancelIntent(taskId: String, offsetSeconds: Long): PendingIntent {
        val intent = Intent(context, DeadlineReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            requestCode(taskId, offsetSeconds),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: PendingIntent.getBroadcast(
            context,
            requestCode(taskId, offsetSeconds),
            intent,
            PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun requestCode(taskId: String, offsetSeconds: Long): Int = 31 * taskId.hashCode() + offsetSeconds.hashCode()

    private fun loadOffsets(taskId: String): List<Long> {
        val raw = prefs.getString(taskId, null) ?: return emptyList()
        return raw.split(',').mapNotNull(String::toLongOrNull)
    }
}
