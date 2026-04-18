package com.mari.app.reminders

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import com.mari.shared.data.repository.TaskRepository
import com.mari.shared.domain.DeviceId
import com.mari.shared.domain.ExecutionRules
import com.mari.shared.domain.FixedClock
import com.mari.shared.domain.Task
import com.mari.shared.domain.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BootRescheduleTest {

    private val clock = FixedClock(Instant.parse("2026-04-18T10:00:00Z"))
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `executing task is rescheduled after boot`() {
        val executing = task("exec-1", TaskStatus.EXECUTING)
        val latch = CountDownLatch(1)
        val fakeRepo = FakeBootRepository(listOf(executing))
        val fakeScheduler = LatchReminderScheduler(latch)

        sendBoot(fakeRepo, fakeScheduler)

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(fakeScheduler.scheduledIds).containsExactly("exec-1")
    }

    @Test
    fun `no scheduling when no executing task exists`() {
        val tasks = listOf(
            task("t1", TaskStatus.TO_BE_DONE),
            task("t2", TaskStatus.COMPLETED),
        )
        val latch = CountDownLatch(1)
        val fakeRepo = FakeBootRepository(tasks, onGetTasks = { latch.countDown() })
        val fakeScheduler = LatchReminderScheduler(CountDownLatch(1))

        sendBoot(fakeRepo, fakeScheduler)

        latch.await(5, TimeUnit.SECONDS)
        assertThat(fakeScheduler.scheduledIds).isEmpty()
    }

    @Test
    fun `wrong intent action is ignored`() {
        val executing = task("exec-1", TaskStatus.EXECUTING)
        val fakeRepo = FakeBootRepository(listOf(executing))
        val fakeScheduler = LatchReminderScheduler(CountDownLatch(1))

        val receiver = BootReceiver()
        injectDependencies(receiver, fakeRepo, fakeScheduler)
        receiver.onReceive(context, Intent("android.intent.action.SCREEN_ON"))

        assertThat(fakeScheduler.scheduledIds).isEmpty()
    }

    // — helpers —

    private fun sendBoot(repo: TaskRepository, scheduler: ReminderScheduler) {
        val receiver = BootReceiver()
        injectDependencies(receiver, repo, scheduler)
        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
    }

    private fun injectDependencies(
        receiver: BootReceiver,
        repo: TaskRepository,
        scheduler: ReminderScheduler,
    ) {
        val repoField = BootReceiver::class.java.getDeclaredField("repository")
        repoField.isAccessible = true
        repoField.set(receiver, repo)

        val schedulerField = BootReceiver::class.java.getDeclaredField("reminderScheduler")
        schedulerField.isAccessible = true
        schedulerField.set(receiver, scheduler)
    }

    private fun task(id: String, status: TaskStatus): Task {
        val base = ExecutionRules.createTask("Task $id", clock, DeviceId.PHONE, id)
        return if (status == TaskStatus.TO_BE_DONE) base
        else ExecutionRules.applyStatusChange(base, status, clock, DeviceId.PHONE)
    }
}

private class FakeBootRepository(
    private val tasks: List<Task>,
    private val onGetTasks: () -> Unit = {},
) : TaskRepository {
    private val flow = MutableStateFlow(tasks)

    override fun observeTasks(): Flow<List<Task>> = flow
    override suspend fun getTasks(): List<Task> = tasks.also { onGetTasks() }
    override suspend fun update(transform: (List<Task>) -> List<Task>): Result<Unit> =
        Result.success(Unit)
}

private class LatchReminderScheduler(
    private val latch: CountDownLatch,
) : ReminderScheduler {
    val scheduledIds = mutableListOf<String>()

    override fun schedule(taskId: String, intervalMs: Long, taskDescription: String) {
        scheduledIds.add(taskId)
        latch.countDown()
    }

    override fun cancel(taskId: String) = Unit
}
