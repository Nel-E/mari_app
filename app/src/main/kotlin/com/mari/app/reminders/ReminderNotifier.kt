package com.mari.app.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_ID = "mari_execution_reminders"
        private const val CHANNEL_NAME = "Execution reminders"
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
