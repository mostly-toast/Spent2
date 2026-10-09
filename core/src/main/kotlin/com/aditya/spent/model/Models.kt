package com.aditya.spent.model

import java.time.Instant

enum class TransactionType { INCOME, EXPENSE, TRANSFER }

enum class TransactionSource { MANUAL, SMS }

enum class ParsedDirection { DEBIT, CREDIT }

data class Account(
    val id: String,
    val name: String,
    val bank: String,
    val type: String,
    val lastFourDigits: String,
    val openingBalancePaise: Long,
    val currentBalancePaise: Long,
)

data class Category(
    val id: String,
    val name: String,
    val type: TransactionType,
    val isArchived: Boolean = false,
)

data class Transaction(
    val id: String,
    val type: TransactionType,
    val amountPaise: Long,
    val accountId: String,
    val destinationAccountId: String? = null,
    val categoryId: String? = null,
    val timestampMillis: Long,
    val counterparty: String? = null,
    val referenceNumber: String? = null,
    val note: String? = null,
    val source: TransactionSource = TransactionSource.MANUAL,
    val originalSmsId: String? = null,
    val balanceAfterPaise: Long? = null,
    val senderHeader: String? = null,
    val smsHash: String? = null,
    val needsReview: Boolean = false,
    val transferId: String? = null,
)

data class RawSms(
    val id: String,
    val sender: String,
    val body: String,
    val timestampMillis: Long,
)

data class ParsedTransaction(
    val amountPaise: Long,
    val direction: ParsedDirection,
    val accountDigits: String?,
    val merchant: String?,
    val timestamp: Instant,
    val referenceNumber: String?,
    val balancePaise: Long?,
    val availableBalancePaise: Long?,
    val rawText: String,
)

enum class ReviewReason {
    UNKNOWN_SENDER,
    UNPARSEABLE,
    NO_ACCOUNT_MATCH,
    AMBIGUOUS_ACCOUNT,
    AMBIGUOUS_TRANSFER,
}

data class ReviewItem(
    val id: String,
    val rawSms: RawSms,
    val reason: ReviewReason,
    val candidate: ParsedTransaction?,
    val createdAt: Instant,
)

object Money {
    fun rupeesToPaise(input: String): Result<Long> = runCatching {
        val trimmed = input.trim()
        require(trimmed.isNotBlank()) { "Amount is required" }
        require(!trimmed.startsWith("-")) { "Amount must be positive" }
        val parts = trimmed.split(".")
        require(parts.size <= 2) { "Invalid amount format" }
        val rupees = parts[0].toLongOrNull() ?: throw NumberFormatException()
        val paise = if (parts.size == 2) {
            val decimal = parts[1]
            require(decimal.length <= 2) { "Maximum two decimal places allowed" }
            (decimal.padEnd(2, '0')).toLong()
        } else 0L
        require(rupees >= 0 && paise >= 0) { "Amount must be positive" }
        val total = rupees * 100 + paise
        require(total > 0) { "Amount must be greater than zero" }
        total
    }

    fun paiseToRupees(paise: Long): String = "INR ${paise / 100}.${(paise % 100).let { if (it < 0) -it else it }.toString().padStart(2, '0')}"
}
