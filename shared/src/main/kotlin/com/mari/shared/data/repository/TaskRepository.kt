package com.mari.shared.data.repository

import com.mari.shared.domain.Task
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun observeTasks(): Flow<List<Task>>
    suspend fun getTasks(): List<Task>
    suspend fun update(transform: (List<Task>) -> List<Task>): Result<Unit>
    suspend fun delete(taskId: String): Result<Unit>
}
