package com.mari.wear.shake

data class ShakeConfig(
    val thresholdMs2: Float = 12f,
    val durationMs: Long = 100L,
    val debounceMs: Long = 1500L,
)
