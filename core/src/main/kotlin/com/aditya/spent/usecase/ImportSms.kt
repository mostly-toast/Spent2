package com.aditya.spent.usecase

import com.aditya.spent.port.SmsSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ImportSms(
    private val smsSource: SmsSource,
    private val ingestSms: IngestSms,
) {
    suspend operator fun invoke(sinceMillis: Long): Flow<IngestResult> =
        smsSource.readAll(sinceMillis).map { ingestSms(it) }
}