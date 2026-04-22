package com.mari.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mari.shared.domain.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class DailyNudgeScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmManager: AlarmManager,
    private val clock: Clock,
) {

    open fun schedule(hour: Int, minute: Int, quietWindow: QuietWindow? = null) {
        val pending = buildPendingIntent()
        val triggerMs = nextTriggerMs(hour, minute, quietWindow)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pending)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pending)
        }
    }

    fun cancel() {
        val pending = buildCancelPendingIntent() ?: return
        alarmManager.cancel(pending)
        pending.cancel()
    }

    fun nextTriggerMs(hour: Int, minute: Int, quietWindow: QuietWindow? = null): Long {
        val now = clock.nowUtc()
        val nowLocal = now.atZone(ZoneId.systemDefault()).toLocalDateTime()
        val target = LocalTime.of(hour, minute)

        val candidateToday = LocalDateTime.of(nowLocal.toLocalDate(), target)
        val candidate = if (candidateToday.isAfter(nowLocal)) candidateToday
        else LocalDateTime.of(LocalDate.from(nowLocal.toLocalDate().plusDays(1)), target)

        val adjusted = if (quietWindow != null && QuietHours.isSuppressed(candidate.toLocalTime(), quietWindow)) {
            val quietEnd = LocalTime.of(quietWindow.endHour, quietWindow.endMinute)
            candidate.toLocalDate().let { date ->
                val sameDay = LocalDateTime.of(date, quietEnd)
                if (sameDay.isAfter(candidate)) sameDay else LocalDateTime.of(date.plusDays(1), quietEnd)
            }
        } else {
            candidate
        }

        return adjusted.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun buildPendingIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, DailyNudgeReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun buildCancelPendingIntent(): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, DailyNudgeReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        const val REQUEST_CODE = 0x4E554447
    }
}
