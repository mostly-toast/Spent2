package com.aditya.spent.port

import com.aditya.spent.model.RawSms
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeSmsSource(private val messages: List<RawSms>) : SmsSource {
    override suspend fun readAll(sinceMillis: Long): Flow<RawSms> = flow {
        messages
            .filter { it.timestampMillis >= sinceMillis }
            .forEach { emit(it) }
    }
}