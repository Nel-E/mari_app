package com.mari.app.reminders

import android.app.AlarmManager
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.mari.app.data.storage.SafGrant
import com.mari.app.data.storage.SafSource
import com.mari.app.data.storage.TaskStorage
import com.mari.app.settings.PhoneSettings
import com.mari.app.settings.SettingsReader
import com.mari.shared.data.serialization.FileSettings
import com.mari.shared.data.serialization.TaskFile
import com.mari.shared.domain.DeviceId
import com.mari.shared.domain.ExecutionRules
import com.mari.shared.domain.Clock
import com.mari.shared.domain.FixedClock
import com.mari.shared.domain.Task
import com.mari.shared.domain.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DailyNudgeLogicTest {

    private val clock = FixedClock(Instant.parse("2026-04-22T08:00:00Z"))
    private val treeUri: Uri = Uri.parse("content://test/tree")

    private lateinit var scheduler: RecordingDailyNudgeScheduler
    private lateinit var notifier: RecordingNotifier

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        scheduler = RecordingDailyNudgeScheduler(context, alarmManager, clock)
        notifier = RecordingNotifier()
    }

    @Test
    fun `EXECUTING task present - postActiveTaskNudge called`() = runTest {
        val executingTask = task("t1", TaskStatus.EXECUTING)
        val logic = logic(tasks = listOf(executingTask), nudgeEnabled = true)

        logic.fire()

        assertThat(notifier.activeNudgeCalled).isTrue()
        assertThat(notifier.pickNudgeCalled).isFalse()
    }

    @Test
    fun `no EXECUTING task - postPickTaskNudge called`() = runTest {
        val toBeDone = task("t1", TaskStatus.TO_BE_DONE)
        val logic = logic(tasks = listOf(toBeDone), nudgeEnabled = true)

        logic.fire()

        assertThat(notifier.pickNudgeCalled).isTrue()
        assertThat(notifier.activeNudgeCalled).isFalse()
    }

    @Test
    fun `deleted EXECUTING task is ignored - postPickTaskNudge called`() = runTest {
        val executing = task("t1", TaskStatus.EXECUTING)
        val deleted = executing.copy(deletedAt = clock.nowUtc())
        val logic = logic(tasks = listOf(deleted), nudgeEnabled = true)

        logic.fire()

        assertThat(notifier.pickNudgeCalled).isTrue()
        assertThat(notifier.activeNudgeCalled).isFalse()
    }

    @Test
    fun `after fire scheduler is re-armed exactly once`() = runTest {
        val logic = logic(tasks = emptyList(), nudgeEnabled = true)

        logic.fire()

        assertThat(scheduler.scheduleCallCount).isEqualTo(1)
    }

    @Test
    fun `when nudge disabled fire does nothing`() = runTest {
        val logic = logic(tasks = emptyList(), nudgeEnabled = false)

        logic.fire()

        assertThat(notifier.activeNudgeCalled).isFalse()
        assertThat(notifier.pickNudgeCalled).isFalse()
        assertThat(scheduler.scheduleCallCount).isEqualTo(0)
    }

    // — helpers —

    private fun logic(tasks: List<Task>, nudgeEnabled: Boolean) = DailyNudgeLogic(
        settingsReader = FakeNudgeSettingsReader(nudgeEnabled),
        safSource = FakeNudgeSafSource(treeUri),
        storage = FakeNudgeStorage(tasks),
        notifier = notifier,
        scheduler = scheduler,
    )

    private fun task(id: String, status: TaskStatus): Task {
        val base = ExecutionRules.createTask("Task $id", clock, DeviceId.PHONE, id = id)
        return if (status == TaskStatus.TO_BE_DONE) base
        else ExecutionRules.applyStatusChange(base, status, clock, DeviceId.PHONE)
    }
}

private class FakeNudgeSettingsReader(private val enabled: Boolean) : SettingsReader {
    override val settings: Flow<PhoneSettings> = flowOf(PhoneSettings(dailyNudgeEnabled = enabled))
    override suspend fun current(): PhoneSettings = PhoneSettings(dailyNudgeEnabled = enabled)
}

private class FakeNudgeSafSource(treeUri: Uri) : SafSource {
    override val grant: StateFlow<SafGrant> = MutableStateFlow(SafGrant.Granted(treeUri))
    override suspend fun init() = Unit
}

private class FakeNudgeStorage(private val tasks: List<Task>) : TaskStorage {
    override fun load(treeUri: Uri): Result<TaskFile> =
        Result.success(TaskFile(tasks = tasks, settings = FileSettings("phone")))
    override fun save(treeUri: Uri, file: TaskFile): Result<Unit> = Result.success(Unit)
    override fun exists(treeUri: Uri): Boolean = true
    override fun initialFile(deviceId: DeviceId): TaskFile =
        TaskFile(tasks = emptyList(), settings = FileSettings(deviceId.name.lowercase()))
}

private class RecordingNotifier : ReminderNotifier(RuntimeEnvironment.getApplication()) {
    var activeNudgeCalled = false
    var pickNudgeCalled = false

    override fun postActiveTaskNudge(task: com.mari.shared.domain.Task, quietWindow: QuietWindow?) {
        activeNudgeCalled = true
    }

    override fun postPickTaskNudge(quietWindow: QuietWindow?) {
        pickNudgeCalled = true
    }
}

private class RecordingDailyNudgeScheduler(
    context: android.content.Context,
    alarmManager: AlarmManager,
    clock: Clock,
) : DailyNudgeScheduler(context, alarmManager, clock) {
    var scheduleCallCount = 0

    override fun schedule(hour: Int, minute: Int, quietWindow: QuietWindow?) {
        scheduleCallCount++
    }
}
