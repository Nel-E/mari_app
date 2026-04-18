package com.mari.app.ui.screens.tasks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mari.shared.domain.TaskStatus

private val FILTER_STATUSES = TaskStatus.entries.toList()

@Composable
fun FilterChipsRow(
    selectedStatuses: Set<TaskStatus>,
    onToggle: (TaskStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FILTER_STATUSES.forEach { status ->
            FilterChip(
                selected = status in selectedStatuses,
                onClick = { onToggle(status) },
                label = { Text(status.displayName()) },
            )
        }
    }
}

private fun TaskStatus.displayName(): String = when (this) {
    TaskStatus.TO_BE_DONE -> "To Do"
    TaskStatus.PAUSED -> "Paused"
    TaskStatus.EXECUTING -> "Executing"
    TaskStatus.QUEUED -> "Queued"
    TaskStatus.COMPLETED -> "Completed"
    TaskStatus.DISCARDED -> "Discarded"
}
