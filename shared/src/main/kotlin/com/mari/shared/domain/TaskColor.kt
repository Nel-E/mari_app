package com.mari.shared.domain

private val HEX_COLOR_REGEX = Regex("^#[0-9A-Fa-f]{6}$")

@JvmInline
value class TaskColor private constructor(val hex: String) {

    companion object {
        fun parse(hex: String): Result<TaskColor> {
            if (!hex.matches(HEX_COLOR_REGEX)) {
                return Result.failure(IllegalArgumentException("Invalid color hex: '$hex'. Expected #RRGGBB"))
            }
            return Result.success(TaskColor(hex.uppercase()))
        }
    }

    override fun toString(): String = hex
}
