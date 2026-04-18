package com.mari.wear.shake

import kotlinx.coroutines.flow.SharedFlow

interface ShakeEventSource {
    val shakeEvents: SharedFlow<Unit>
}
