package com.mari.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mari.shared.data.repository.TaskRepository
import com.mari.shared.domain.TaskStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: TaskRepository

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val executing = repository.getTasks().firstOrNull { it.status == TaskStatus.EXECUTING }
                if (executing != null) {
                    reminderScheduler.schedule(executing.id, DEFAULT_INTERVAL_MS, executing.description)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val DEFAULT_INTERVAL_MS = 30 * 60 * 1000L
    }
}
