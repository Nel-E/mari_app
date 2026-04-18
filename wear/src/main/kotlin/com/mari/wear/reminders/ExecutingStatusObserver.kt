package com.mari.wear.reminders

import com.mari.shared.data.repository.TaskRepository
import com.mari.shared.domain.TaskStatus
import com.mari.wear.di.ApplicationScope
import com.mari.wear.settings.WearSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExecutingStatusObserver @Inject constructor(
    private val repository: TaskRepository,
    private val reminderScheduler: ReminderScheduler,
    private val settingsRepository: WearSettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private var scheduledTaskId: String? = null

    fun start() {
        scope.launch {
            combine(
                repository.observeTasks().map { tasks -> tasks.firstOrNull { it.status == TaskStatus.EXECUTING } },
                settingsRepository.settings,
            ) { executingTask, settings -> executingTask to settings }
                .distinctUntilChangedBy { it.first?.id to it.second.reminderEnabled to it.second.reminderIntervalMinutes }
                .collect { (executingTask, settings) ->
                    scheduledTaskId?.let { reminderScheduler.cancel(it) }
                    scheduledTaskId = null
                    if (executingTask != null && settings.reminderEnabled) {
                        reminderScheduler.schedule(
                            executingTask.id,
                            settings.reminderIntervalMinutes * 60_000L,
                            executingTask.description,
                        )
                        scheduledTaskId = executingTask.id
                    }
                }
        }
    }
}
