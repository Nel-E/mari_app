package com.mari.app.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.mari.shared.domain.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_ID = "mari_execution_reminders"
        private const val CHANNEL_NAME = "Execution reminders"
        const val DAILY_NUDGE_ID = 0x4E554448
    }

    private val notificationManager: NotificationManager =
        context.getSystemService()!!

    init {
        createChannel()
    }

    fun notify(taskId: String, taskDescription: String, quietWindow: QuietWindow? = null) {
        if (isDndActive()) return
        if (quietWindow != null && QuietHours.isSuppressed(LocalTime.now(), quietWindow)) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Task in progress")
            .setContentText(taskDescription)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(taskId.hashCode(), notification)
    }

    fun notifyDeadline(
        taskId: String,
        title: String,
        taskName: String,
        dueAt: Instant,
        quietWindow: QuietWindow? = null,
    ) {
        if (isDndActive()) return
        if (quietWindow != null && QuietHours.isSuppressed(LocalTime.now(), quietWindow)) return

        val formatter = DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault())
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText("$taskName - due ${formatter.format(dueAt)}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(("deadline:$taskId").hashCode(), notification)
    }

    open fun postActiveTaskNudge(task: Task, quietWindow: QuietWindow? = null) {
        if (isDndActive()) return
        if (quietWindow != null && QuietHours.isSuppressed(LocalTime.now(), quietWindow)) return

        val completeIntent = PendingIntent.getBroadcast(
            context,
            task.id.hashCode(),
            Intent(context, NudgeActionReceiver::class.java).apply {
                action = NudgeActionReceiver.ACTION_COMPLETE
                putExtra(NudgeActionReceiver.EXTRA_TASK_ID, task.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val changeIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, NudgeActionReceiver::class.java).apply {
                action = NudgeActionReceiver.ACTION_CHANGE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Still on ${task.name}?")
            .setContentText("Tap to continue, or use actions below.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .addAction(0, "Complete", completeIntent)
            .addAction(0, "Change", changeIntent)
            .build()

        notificationManager.notify(DAILY_NUDGE_ID, notification)
    }

    open fun postPickTaskNudge(quietWindow: QuietWindow? = null) {
        if (isDndActive()) return
        if (quietWindow != null && QuietHours.isSuppressed(LocalTime.now(), quietWindow)) return

        val openIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, NudgeActionReceiver::class.java).apply {
                action = NudgeActionReceiver.ACTION_CHANGE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Pick a task for today")
            .setContentText("No task is currently active.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()

        notificationManager.notify(DAILY_NUDGE_ID, notification)
    }

    private fun isDndActive(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return notificationManager.currentInterruptionFilter ==
                NotificationManager.INTERRUPTION_FILTER_NONE
        }
        return false
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        notificationManager.createNotificationChannel(channel)
    }
}
