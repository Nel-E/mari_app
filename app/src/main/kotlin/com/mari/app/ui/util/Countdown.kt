package com.mari.app.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

@Composable
fun rememberCountdown(totalMs: Long, tickMs: Long = 100L): State<Long> =
    produceState(initialValue = totalMs) {
        val start = System.currentTimeMillis()
        while (value > 0L) {
            delay(tickMs)
            value = maxOf(0L, totalMs - (System.currentTimeMillis() - start))
        }
    }
