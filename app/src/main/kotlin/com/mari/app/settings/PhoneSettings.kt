package com.mari.app.settings

import com.mari.app.reminders.QuietWindow
import com.mari.app.shake.ShakeConfig

data class PhoneSettings(
    val shakeStrength: Float = 15f,
    val shakeDurationMs: Long = 300L,
    val shakeSoundUri: String? = null,
    val shakeVibrate: Boolean = true,
    val reminderEnabled: Boolean = false,
    val reminderIntervalMinutes: Int = 30,
    val reminderSoundUri: String? = null,
    val reminderVibrate: Boolean = true,
    val quietStartHour: Int = 22,
    val quietStartMinute: Int = 0,
    val quietEndHour: Int = 7,
    val quietEndMinute: Int = 0,
) {
    val shakeConfig: ShakeConfig
        get() = ShakeConfig(
            thresholdMs2 = shakeStrength,
            durationMs = shakeDurationMs,
        )

    val quietWindow: QuietWindow
        get() = QuietWindow(
            startHour = quietStartHour,
            startMinute = quietStartMinute,
            endHour = quietEndHour,
            endMinute = quietEndMinute,
        )
}
