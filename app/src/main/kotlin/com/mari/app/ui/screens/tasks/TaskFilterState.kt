package com.mari.app.ui.screens.tasks

import com.mari.shared.domain.TaskStatus

data class TaskFilterState(
    val selectedStatuses: Set<TaskStatus> = emptySet(),
    val query: String = "",
    val sortMode: TaskSortMode = TaskSortMode.DEFAULT,
)
