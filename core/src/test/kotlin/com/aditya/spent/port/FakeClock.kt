package com.aditya.spent.port

import java.time.Instant

class FakeClock(private var currentTime: Instant = Instant.EPOCH) : Clock {
    override fun now(): Instant = currentTime

    fun advance(duration: java.time.Duration) {
        currentTime = currentTime.plus(duration)
    }

    fun setTime(instant: Instant) {
        currentTime = instant
    }
}