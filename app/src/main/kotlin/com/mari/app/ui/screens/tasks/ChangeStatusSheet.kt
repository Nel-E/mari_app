package com.mari.app.ui.screens.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mari.shared.domain.TaskStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeStatusSheet(
    currentStatus: TaskStatus,
    sheetState: SheetState,
    onSelect: (TaskStatus) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = "Change status",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            TaskStatus.entries.forEach { status ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(status) }
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = status == currentStatus,
                        onClick = { onSelect(status) },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = status.label())
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private fun TaskStatus.label(): String = when (this) {
    TaskStatus.TO_BE_DONE -> "To Do"
    TaskStatus.PAUSED -> "Paused"
    TaskStatus.EXECUTING -> "Executing"
    TaskStatus.QUEUED -> "Queued"
    TaskStatus.COMPLETED -> "Completed"
    TaskStatus.DISCARDED -> "Discarded"
}
