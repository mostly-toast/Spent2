package com.aditya.spent.port

import com.aditya.spent.model.RawSms
import kotlinx.coroutines.flow.Flow

interface SmsSource {
    suspend fun readAll(sinceMillis: Long): Flow<RawSms>
}