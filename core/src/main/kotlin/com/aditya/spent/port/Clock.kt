package com.aditya.spent.port

import java.time.Instant

interface Clock {
    fun now(): Instant
}