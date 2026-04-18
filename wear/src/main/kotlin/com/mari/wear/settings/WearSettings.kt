package com.mari.wear.settings

import com.mari.wear.shake.ShakeConfig

data class WearSettings(
    val shakeStrength: Float = 15f,
    val shakeDurationMs: Long = 300L,
    val shakeVibrate: Boolean = true,
    val reminderEnabled: Boolean = false,
    val reminderIntervalMinutes: Int = 30,
    val reminderVibrate: Boolean = true,
) {
    val shakeConfig: ShakeConfig
        get() = ShakeConfig(
            thresholdMs2 = shakeStrength,
            durationMs = shakeDurationMs,
        )
}
