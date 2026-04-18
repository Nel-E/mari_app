package com.mari.app.reminders

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ReminderRouterTest {

    private lateinit var alarmScheduler: FakeReminderScheduler
    private lateinit var workManagerScheduler: FakeReminderScheduler
    private lateinit var router: ReminderRouter

    @Before
    fun setUp() {
        alarmScheduler = FakeReminderScheduler()
        workManagerScheduler = FakeReminderScheduler()
        router = ReminderRouter(
            alarmScheduler = alarmScheduler,
            workManagerScheduler = workManagerScheduler,
        )
    }

    @Test
    fun `schedule below threshold routes to alarm scheduler`() {
        router.schedule("task1", BELOW_THRESHOLD, "desc")

        assertEquals(listOf("task1"), alarmScheduler.scheduled)
        assertEquals(emptyList<String>(), workManagerScheduler.scheduled)
    }

    @Test
    fun `schedule at threshold routes to work manager`() {
        router.schedule("task1", THRESHOLD, "desc")

        assertEquals(emptyList<String>(), alarmScheduler.scheduled)
        assertEquals(listOf("task1"), workManagerScheduler.scheduled)
    }

    @Test
    fun `schedule above threshold routes to work manager`() {
        router.schedule("task1", ABOVE_THRESHOLD, "desc")

        assertEquals(emptyList<String>(), alarmScheduler.scheduled)
        assertEquals(listOf("task1"), workManagerScheduler.scheduled)
    }

    @Test
    fun `cancel hits both schedulers`() {
        router.cancel("task1")

        assertEquals(listOf("task1"), alarmScheduler.cancelled)
        assertEquals(listOf("task1"), workManagerScheduler.cancelled)
    }

    companion object {
        private const val THRESHOLD = 15 * 60 * 1000L
        private const val BELOW_THRESHOLD = THRESHOLD - 1
        private const val ABOVE_THRESHOLD = THRESHOLD + 1
    }
}

private class FakeReminderScheduler : ReminderScheduler {
    val scheduled = mutableListOf<String>()
    val cancelled = mutableListOf<String>()

    override fun schedule(taskId: String, intervalMs: Long, taskDescription: String) {
        scheduled.add(taskId)
    }

    override fun cancel(taskId: String) {
        cancelled.add(taskId)
    }
}
