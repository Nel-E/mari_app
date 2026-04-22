package com.mari.app.ui.screens.tasks

import com.mari.shared.domain.TaskListing

enum class TaskSortMode(val label: String, val shared: TaskListing.SortMode) {
    DEFAULT("Default", TaskListing.SortMode.DEFAULT),
    A_Z("A – Z", TaskListing.SortMode.A_Z),
    Z_A("Z – A", TaskListing.SortMode.Z_A),
    DUE_SOONEST("Due soonest", TaskListing.SortMode.DUE_SOONEST),
    CREATED_NEWEST("Created (newest)", TaskListing.SortMode.CREATED_NEWEST),
    CREATED_OLDEST("Created (oldest)", TaskListing.SortMode.CREATED_OLDEST),
    MODIFIED_NEWEST("Modified (newest)", TaskListing.SortMode.MODIFIED_NEWEST),
    MODIFIED_OLDEST("Modified (oldest)", TaskListing.SortMode.MODIFIED_OLDEST),
}
