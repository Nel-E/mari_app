package com.mari.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import com.google.common.truth.Truth.assertThat
import com.mari.shared.domain.FixedClock
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DailyNudgeSchedulerTest {

    private lateinit var alarmManager: AlarmManager
    private lateinit var scheduler: DailyNudgeScheduler

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        alarmManager = context.getSystemService(AlarmManager::class.java)
        scheduler = DailyNudgeScheduler(context, alarmManager, FixedClock(Instant.parse("2026-04-22T08:00:00Z")))
    }

    @Test
    fun `schedule at 9h00 when current time is 8h00 triggers today`() {
        val clock = FixedClock(Instant.parse("2026-04-22T08:00:00Z"))
        val s = DailyNudgeScheduler(RuntimeEnvironment.getApplication(), alarmManager, clock)

        val triggerMs = s.nextTriggerMs(9, 0)

        val triggerLocal = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(triggerMs),
            ZoneId.systemDefault(),
        )
        assertThat(triggerLocal.hour).isEqualTo(9)
        assertThat(triggerLocal.minute).isEqualTo(0)
        assertThat(triggerLocal.toLocalDate().toString()).isEqualTo(
            Instant.parse("2026-04-22T08:00:00Z").atZone(ZoneId.systemDefault()).toLocalDate().toString(),
        )
    }

    @Test
    fun `schedule at 9h00 when current time is 9h30 triggers next day`() {
        val clock = FixedClock(Instant.parse("2026-04-22T09:30:00Z"))
        val s = DailyNudgeScheduler(RuntimeEnvironment.getApplication(), alarmManager, clock)

        val triggerMs = s.nextTriggerMs(9, 0)

        val triggerLocal = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(triggerMs),
            ZoneId.systemDefault(),
        )
        assertThat(triggerLocal.hour).isEqualTo(9)
        assertThat(triggerLocal.minute).isEqualTo(0)
        val expectedDate = Instant.parse("2026-04-22T09:30:00Z")
            .atZone(ZoneId.systemDefault()).toLocalDate().plusDays(1)
        assertThat(triggerLocal.toLocalDate()).isEqualTo(expectedDate)
    }

    @Test
    fun `trigger inside quiet hours is bumped to quiet-hours end`() {
        val clock = FixedClock(Instant.parse("2026-04-22T20:00:00Z"))
        val s = DailyNudgeScheduler(RuntimeEnvironment.getApplication(), alarmManager, clock)
        val quietWindow = QuietWindow(startHour = 22, startMinute = 0, endHour = 7, endMinute = 0)

        // 6:30 would land in quiet window (22:00–07:00) on the next calendar day
        val triggerMs = s.nextTriggerMs(6, 30, quietWindow)
        val triggerLocal = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(triggerMs),
            ZoneId.systemDefault(),
        )
        assertThat(triggerLocal.hour).isEqualTo(7)
        assertThat(triggerLocal.minute).isEqualTo(0)
    }

    @Test
    fun `cancel removes the scheduled alarm`() {
        scheduler.schedule(9, 0)

        scheduler.cancel()

        val shadow = shadowOf(alarmManager)
        val alarms = shadow.scheduledAlarms
        assertThat(alarms).isEmpty()
    }

    @Test
    fun `schedule sets exactly one alarm`() {
        scheduler.schedule(9, 0)

        val shadow = shadowOf(alarmManager)
        assertThat(shadow.scheduledAlarms).hasSize(1)
    }

    @Test
    fun `re-schedule replaces existing alarm`() {
        scheduler.schedule(9, 0)
        scheduler.schedule(10, 0)

        val shadow = shadowOf(alarmManager)
        assertThat(shadow.scheduledAlarms).hasSize(1)
    }
}
