package com.mari.app.reminders

import com.mari.app.data.storage.SafGrant
import com.mari.app.data.storage.SafSource
import com.mari.app.data.storage.TaskStorage
import com.mari.app.settings.SettingsReader
import com.mari.shared.domain.Task
import com.mari.shared.domain.TaskStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DailyNudgeLogic @Inject constructor(
    private val settingsReader: SettingsReader,
    private val safSource: SafSource,
    private val storage: TaskStorage,
    private val notifier: ReminderNotifier,
    private val scheduler: DailyNudgeScheduler,
) {

    suspend fun fire() {
        val settings = settingsReader.current()
        if (!settings.dailyNudgeEnabled) return

        val tasks = loadTasks()
        val active = tasks.firstOrNull { it.status == TaskStatus.EXECUTING && it.deletedAt == null }
        if (active != null) {
            notifier.postActiveTaskNudge(active, settings.quietWindow)
        } else {
            notifier.postPickTaskNudge(settings.quietWindow)
        }

        scheduler.schedule(settings.dailyNudgeHour, settings.dailyNudgeMinute, settings.quietWindow)
    }

    private suspend fun loadTasks(): List<Task> {
        safSource.init()
        val grant = safSource.grant.value
        if (grant !is SafGrant.Granted || !storage.exists(grant.treeUri)) return emptyList()
        return storage.load(grant.treeUri).getOrNull()?.tasks.orEmpty()
    }
}
