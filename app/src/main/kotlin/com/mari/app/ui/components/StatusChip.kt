package com.mari.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mari.app.ui.theme.ColorStatusCompleted
import com.mari.app.ui.theme.ColorStatusDiscarded
import com.mari.app.ui.theme.ColorStatusExecuting
import com.mari.app.ui.theme.ColorStatusPaused
import com.mari.app.ui.theme.ColorStatusToBeDone
import com.mari.shared.domain.TaskPriority
import com.mari.shared.domain.TaskStatus

@Composable
fun StatusChip(
    status: TaskStatus,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val (label, color) = when (status) {
        TaskStatus.TO_BE_DONE -> "To Do" to ColorStatusToBeDone
        TaskStatus.PAUSED -> "Paused" to ColorStatusPaused
        TaskStatus.EXECUTING -> "Executing" to ColorStatusExecuting
        TaskStatus.COMPLETED -> "Completed" to ColorStatusCompleted
        TaskStatus.DISCARDED -> "Discarded" to ColorStatusDiscarded
        else -> "To Do" to ColorStatusToBeDone
    }
    AssistChip(
        onClick = onClick,
        label = { Text(text = label) },
        modifier = modifier
            .padding(end = 4.dp)
            .semantics { contentDescription = "Status $label" },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = color.copy(alpha = 0.15f),
            labelColor = color,
        ),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
    )
}

@Composable
fun PriorityChip(
    priority: TaskPriority,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val (label, color) = when (priority) {
        TaskPriority.LOW -> "Low" to Color(0xFF546E7A)
        TaskPriority.NORMAL -> "Normal" to Color(0xFF5E6AD2)
        TaskPriority.HIGH -> "High" to Color(0xFFB26A00)
        TaskPriority.VERY_HIGH -> "Very high" to Color(0xFFB3261E)
    }
    AssistChip(
        onClick = onClick,
        label = { Text(text = label) },
        modifier = modifier
            .padding(end = 4.dp)
            .semantics { contentDescription = "Priority $label" },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = color.copy(alpha = 0.15f),
            labelColor = color,
        ),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
    )
}
