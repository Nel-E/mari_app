package com.mari.app.ui.components

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
import com.mari.app.ui.theme.ColorStatusQueued
import com.mari.app.ui.theme.ColorStatusToBeDone
import com.mari.shared.domain.TaskStatus

@Composable
fun StatusChip(
    status: TaskStatus,
    modifier: Modifier = Modifier,
) {
    val (label, color) = status.labelAndColor()
    AssistChip(
        onClick = {},
        label = { Text(text = label) },
        modifier = modifier
            .padding(end = 4.dp)
            .semantics { contentDescription = "Status $label" },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = color.copy(alpha = 0.15f),
            labelColor = color,
        ),
        border = AssistChipDefaults.assistChipBorder(
            borderColor = color.copy(alpha = 0.4f),
        ),
    )
}

private fun TaskStatus.labelAndColor(): Pair<String, Color> = when (this) {
    TaskStatus.TO_BE_DONE -> "To Do" to ColorStatusToBeDone
    TaskStatus.PAUSED -> "Paused" to ColorStatusPaused
    TaskStatus.EXECUTING -> "Executing" to ColorStatusExecuting
    TaskStatus.QUEUED -> "Queued" to ColorStatusQueued
    TaskStatus.COMPLETED -> "Completed" to ColorStatusCompleted
    TaskStatus.DISCARDED -> "Discarded" to ColorStatusDiscarded
}
