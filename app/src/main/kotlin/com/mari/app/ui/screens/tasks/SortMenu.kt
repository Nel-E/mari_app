package com.mari.app.ui.screens.tasks

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun SortMenu(
    currentMode: TaskSortMode,
    onSelect: (TaskSortMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
        Text("Sort")
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        TaskSortMode.entries.forEach { mode ->
            DropdownMenuItem(
                text = { Text(mode.label) },
                onClick = {
                    onSelect(mode)
                    expanded = false
                },
                trailingIcon = if (mode == currentMode) {
                    { Text("✓") }
                } else {
                    null
                },
            )
        }
    }
}
