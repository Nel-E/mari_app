package com.mari.app.reminders

import java.time.LocalTime

data class QuietWindow(
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
)

object QuietHours {

    fun isSuppressed(now: LocalTime, window: QuietWindow): Boolean {
        val start = LocalTime.of(window.startHour, window.startMinute)
        val end = LocalTime.of(window.endHour, window.endMinute)
        return if (start <= end) {
            now >= start && now < end
        } else {
            // Overnight window, e.g. 22:00–07:00
            now >= start || now < end
        }
    }
}
