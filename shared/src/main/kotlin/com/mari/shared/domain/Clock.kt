package com.mari.shared.domain

import java.time.Instant

interface Clock {
    fun nowUtc(): Instant
}

object SystemClock : Clock {
    override fun nowUtc(): Instant = Instant.now()
}

class FixedClock(private val fixed: Instant) : Clock {
    override fun nowUtc(): Instant = fixed
}
