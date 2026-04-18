package com.mari.app.shake

import kotlinx.coroutines.flow.SharedFlow

interface ShakeEventSource {
    val shakeEvents: SharedFlow<Unit>
}
