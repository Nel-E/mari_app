package com.mari.app.reminders

import android.app.AlarmManager
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.mari.app.data.storage.SafGrant
import com.mari.app.data.storage.SafSource
import com.mari.app.data.storage.TaskStorage
import com.mari.app.reminders.DailyNudgeScheduler
import com.mari.app.settings.PhoneSettings
import com.mari.app.settings.SettingsReader
import com.mari.shared.data.serialization.FileSettings
import com.mari.shared.data.serialization.TaskFile
import com.mari.shared.domain.DeviceId
import com.mari.shared.domain.ExecutionRules
import com.mari.shared.domain.FixedClock
import com.mari.shared.domain.Task
import com.mari.shared.domain.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BootRescheduleTest {

    private val clock = FixedClock(Instant.parse("2026-04-18T10:00:00Z"))
    private val treeUri: Uri = Uri.parse("content://test/tree")

    @Test
    fun `executing task is rescheduled after boot`() = runTest {
        val executing = task("exec-1", TaskStatus.EXECUTING)
        val fakeScheduler = RecordingReminderScheduler()
        val rescheduler = rescheduler(tasks = listOf(executing), scheduler = fakeScheduler)

        rescheduler.rescheduleAll()

        assertThat(fakeScheduler.scheduledIds).containsExactly("exec-1")
    }

    @Test
    fun `no scheduling when no executing task exists`() = runTest {
        val tasks = listOf(task("t1", TaskStatus.TO_BE_DONE), task("t2", TaskStatus.COMPLETED))
        val fakeScheduler = RecordingReminderScheduler()
        val rescheduler = rescheduler(tasks = tasks, scheduler = fakeScheduler)

        rescheduler.rescheduleAll()

        assertThat(fakeScheduler.scheduledIds).isEmpty()
    }

    @Test
    fun `only first executing task is scheduled when multiple exist`() = runTest {
        val exec1 = task("exec-1", TaskStatus.EXECUTING)
        val exec2 = task("exec-2", TaskStatus.EXECUTING)
        val fakeScheduler = RecordingReminderScheduler()
        val rescheduler = rescheduler(tasks = listOf(exec1, exec2), scheduler = fakeScheduler)

        rescheduler.rescheduleAll()

        assertThat(fakeScheduler.scheduledIds).hasSize(1)
    }

    // — helpers —

    private fun rescheduler(
        tasks: List<Task>,
        scheduler: ReminderScheduler,
    ): BootRescheduler {
        val context = RuntimeEnvironment.getApplication()
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val nudgeScheduler = DailyNudgeScheduler(context, alarmManager, clock)
        return BootRescheduler(
            safSource = FakeBootSafSource(treeUri),
            storage = FakeBootStorage(tasks),
            reminderScheduler = scheduler,
            deadlineReminderScheduler = NoopDeadlineReminderScheduler(),
            settingsRepository = FakeSettingsReader(),
            dailyNudgeScheduler = nudgeScheduler,
        )
    }

    private fun task(id: String, status: TaskStatus): Task {
        val base = ExecutionRules.createTask("Task $id", clock, DeviceId.PHONE, id = id)
        return if (status == TaskStatus.TO_BE_DONE) base
        else ExecutionRules.applyStatusChange(base, status, clock, DeviceId.PHONE)
    }
}

private class FakeBootSafSource(treeUri: Uri) : SafSource {
    override val grant: StateFlow<SafGrant> = MutableStateFlow(SafGrant.Granted(treeUri))
    override suspend fun init() = Unit
}

private class FakeBootStorage(
    private val tasks: List<Task>,
) : TaskStorage {
    override fun load(treeUri: Uri): Result<TaskFile> =
        Result.success(TaskFile(tasks = tasks, settings = FileSettings("phone")))
    override fun save(treeUri: Uri, file: TaskFile): Result<Unit> = Result.success(Unit)
    override fun exists(treeUri: Uri): Boolean = true
    override fun initialFile(deviceId: DeviceId): TaskFile =
        TaskFile(tasks = emptyList(), settings = FileSettings(deviceId.name.lowercase()))
}

private class RecordingReminderScheduler : ReminderScheduler {
    val scheduledIds = mutableListOf<String>()

    override fun schedule(taskId: String, intervalMs: Long, taskDescription: String) {
        scheduledIds.add(taskId)
    }

    override fun cancel(taskId: String) = Unit
}

private class NoopDeadlineReminderScheduler : DeadlineReminderScheduler {
    override fun schedule(task: Task) = Unit
    override fun cancel(taskId: String) = Unit
}

private class FakeSettingsReader : SettingsReader {
    override val settings: Flow<PhoneSettings> = flowOf(PhoneSettings())
    override suspend fun current(): PhoneSettings = PhoneSettings()
}
