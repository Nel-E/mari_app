package com.mari.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mari.shared.domain.Task
import java.time.Instant

@Composable
fun TaskRow(
    task: Task,
    onClick: () -> Unit,
    onPriorityClick: (Task) -> Unit = {},
    onStatusClick: (Task) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .semantics { contentDescription = "Task ${task.name}" }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val colorHex = task.colorHex
            if (!colorHex.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(52.dp)
                        .background(parseColor(colorHex), shape = RoundedCornerShape(999.dp)),
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (task.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                task.dueAt?.let { dueAt ->
                    val now = remember { Instant.now() }
                    val isOverdue = dueAt.isBefore(now)
                    Spacer(modifier = Modifier.height(4.dp))
                    // Always show the due date line
                    Text(
                        text = if (isOverdue) formatAbsoluteDueDate(dueAt) else formatDueInText(dueAt, now),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                    // If overdue, show the elapsed overdue duration on a second line
                    if (isOverdue) {
                        Text(
                            text = formatOverdueText(dueAt, now),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                PriorityChip(
                    priority = task.priority,
                    onClick = { onPriorityClick(task) },
                )
                Spacer(modifier = Modifier.height(4.dp))
                StatusChip(
                    status = task.status,
                    onClick = { onStatusClick(task) },
                )
            }
        }
    }
}

private fun parseColor(raw: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(raw)) }.getOrDefault(Color.Transparent)
