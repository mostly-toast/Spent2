package com.aditya.spent.rules

import java.time.Duration

object Constants {
    val DEDUP_WEAK_WINDOW: Duration = Duration.ofMinutes(2)
    val TRANSFER_MATCH_WINDOW: Duration = Duration.ofMinutes(10)
}