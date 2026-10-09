package com.aditya.spent.usecase

import com.aditya.spent.model.RawSms

class ProcessIncomingSms(
    private val ingestSms: IngestSms,
) {
    suspend operator fun invoke(rawSms: RawSms): IngestResult = ingestSms(rawSms)
}