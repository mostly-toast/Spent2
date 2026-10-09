package com.aditya.spent.sms

import com.aditya.spent.model.ParsedTransaction
import com.aditya.spent.model.RawSms
import com.aditya.spent.port.TransactionExtractor
import java.time.Instant

object Parser : TransactionExtractor {

    override fun extract(rawSms: RawSms): ParsedTransaction? {
        val body = rawSms.body
        val direction = Extractors.direction(body) ?: return null
        val amountPaise = Extractors.amount(body) ?: return null
        return ParsedTransaction(
            amountPaise = amountPaise,
            direction = direction,
            accountDigits = Extractors.accountDigits(body),
            merchant = Extractors.counterparty(body, direction),
            timestamp = Extractors.date(body, Instant.ofEpochMilli(rawSms.timestampMillis)),
            referenceNumber = Extractors.reference(body),
            balancePaise = Extractors.balance(body),
            availableBalancePaise = Extractors.availableBalance(body),
            rawText = body,
        )
    }
}