package com.mari.shared.domain

object TaskValidation {
    private const val MAX_DESCRIPTION_LENGTH = 500

    fun validateDescription(raw: String): Result<String> {
        val trimmed = raw.trim()
        return when {
            trimmed.isBlank() -> Result.failure(IllegalArgumentException("Task description must not be blank"))
            trimmed.length > MAX_DESCRIPTION_LENGTH ->
                Result.failure(IllegalArgumentException("Task description must be $MAX_DESCRIPTION_LENGTH characters or fewer"))
            else -> Result.success(trimmed)
        }
    }
}
