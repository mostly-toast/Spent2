package com.aditya.spent.port

import com.aditya.spent.model.ParsedTransaction
import com.aditya.spent.model.RawSms

interface TransactionExtractor {
    fun extract(rawSms: RawSms): ParsedTransaction?
}