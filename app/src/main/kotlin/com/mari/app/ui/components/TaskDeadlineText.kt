package com.mari.app.ui.components

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Format a positive [totalMinutes] value as a human-readable duration string. */
fun formatDuration(totalMinutes: Long): String {
    val m = maxOf(totalMinutes, 0L)
    return when {
        m < 60L -> "$m minute${if (m == 1L) "" else "s"}"
        m < 24L * 60L -> {
            val hours = m / 60L
            val mins = m % 60L
            val h = "$hours hour${if (hours == 1L) "" else "s"}"
            if (mins == 0L) h else "$h $mins minute${if (mins == 1L) "" else "s"}"
        }
        else -> {
            val days = m / (24L * 60L)
            val rem = m % (24L * 60L)
            val hours = rem / 60L
            val mins = rem % 60L
            buildString {
                append("$days day${if (days == 1L) "" else "s"}")
                if (hours > 0L) append(" $hours hour${if (hours == 1L) "" else "s"}")
                if (mins > 0L) append(" $mins minute${if (mins == 1L) "" else "s"}")
            }
        }
    }
}

/** "Due in 34 minutes" / "Due in 1 hour 14 minutes" / "Due in 1 day 10 hours 14 minutes" */
fun formatDueInText(dueAt: Instant, now: Instant = Instant.now()): String {
    val totalMinutes = maxOf(Duration.between(now, dueAt).toMinutes(), 0L)
    return "Due in ${formatDuration(totalMinutes)}"
}

/** "Overdue: 34 minutes" / "Overdue: 1 hour 14 minutes" / "Overdue: 1 day 3 hours" */
fun formatOverdueText(dueAt: Instant, now: Instant = Instant.now()): String {
    val totalMinutes = maxOf(Duration.between(dueAt, now).toMinutes(), 0L)
    return "Overdue: ${formatDuration(totalMinutes)}"
}

/** "Due: Apr 27, 2026 at 08:00" */
fun formatAbsoluteDueDate(dueAt: Instant): String {
    val zdt = dueAt.atZone(ZoneId.systemDefault())
    val date = zdt.toLocalDate().format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    val time = zdt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
    return "Due: $date at $time"
}

/** Legacy single-line helper used by other callers. */
fun formatDueText(dueAt: Instant, now: Instant = Instant.now()): String =
    if (dueAt.isBefore(now)) formatOverdueText(dueAt, now) else formatDueInText(dueAt, now)
