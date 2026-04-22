package com.mari.app.ui.components

import java.time.Duration
import java.time.Instant

fun formatDueText(dueAt: Instant, now: Instant = Instant.now()): String {
    val delta = Duration.between(now, dueAt)
    val minutes = delta.toMinutes()
    return when {
        minutes < 0 -> "Overdue ${-minutes}m"
        minutes < 60 -> "Due in ${minutes}m"
        minutes < 24 * 60 -> "Due in ${delta.toHours()}h"
        minutes < 48 * 60 -> "Due tomorrow"
        else -> "Due in ${delta.toDays()}d"
    }
}
