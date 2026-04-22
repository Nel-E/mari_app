package com.mari.shared.domain

object TaskValidation {
    private const val MAX_NAME_LENGTH = 120
    private const val MAX_DESCRIPTION_LENGTH = 500

    fun validateName(raw: String): Result<String> {
        val trimmed = raw.trim()
        return when {
            trimmed.isBlank() -> Result.failure(IllegalArgumentException("Task name must not be blank"))
            trimmed.length > MAX_NAME_LENGTH ->
                Result.failure(IllegalArgumentException("Task name must be $MAX_NAME_LENGTH characters or fewer"))
            else -> Result.success(trimmed)
        }
    }

    fun validateDescription(raw: String): Result<String> {
        val trimmed = raw.trim()
        return when {
            trimmed.isBlank() -> Result.success("")
            trimmed.length > MAX_DESCRIPTION_LENGTH ->
                Result.failure(IllegalArgumentException("Task notes must be $MAX_DESCRIPTION_LENGTH characters or fewer"))
            else -> Result.success(trimmed)
        }
    }

    fun findDuplicateName(
        tasks: List<Task>,
        candidate: String,
        excludeId: String? = null,
    ): String? {
        val normalized = candidate.trim().lowercase()
        if (normalized.isBlank()) return null
        return tasks.firstOrNull {
            it.deletedAt == null &&
                it.id != excludeId &&
                it.name.trim().lowercase() == normalized
        }?.name
    }

    fun requireUniqueNames(tasks: List<Task>): Result<Unit> {
        val seen = mutableSetOf<String>()
        tasks.filter { it.deletedAt == null }.forEach { task ->
            val normalized = task.name.trim().lowercase()
            if (normalized.isBlank()) {
                return Result.failure(IllegalArgumentException("Task name must not be blank"))
            }
            if (!seen.add(normalized)) {
                return Result.failure(IllegalArgumentException("Duplicate task names are not allowed"))
            }
        }
        return Result.success(Unit)
    }
}
