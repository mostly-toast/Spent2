package com.aditya.spent.rules

import com.aditya.spent.model.ParsedDirection
import com.aditya.spent.model.ParsedTransaction
import com.aditya.spent.model.Transaction
import com.aditya.spent.model.TransactionType
import com.aditya.spent.repository.TransactionRepository
import java.security.MessageDigest
import java.time.Duration

object Dedup {
    private const val HASH_ALGORITHM = "SHA-256"

    sealed interface Verdict {
        data class Duplicate(val existing: Transaction, val layer: Int) : Verdict
        data class PossibleDuplicate(val existing: Transaction) : Verdict
        object Unique : Verdict
    }

    fun computeSmsHash(sender: String, body: String): String {
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
        return digest.digest("$sender\n$body".toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun transactionTypeOf(direction: ParsedDirection): TransactionType = when (direction) {
        ParsedDirection.DEBIT -> TransactionType.EXPENSE
        ParsedDirection.CREDIT -> TransactionType.INCOME
    }

    suspend fun evaluate(
        parsed: ParsedTransaction,
        accountId: String,
        senderHeader: String,
        smsHash: String,
        transactions: TransactionRepository,
        weakWindow: Duration = Constants.DEDUP_WEAK_WINDOW,
    ): Verdict {
        transactions.findBySmsHash(smsHash)?.let { return Verdict.Duplicate(it, LAYER_EXACT_REPEAT) }

        parsed.referenceNumber?.let { reference ->
            transactions.findByReference(accountId, reference)?.let { return Verdict.Duplicate(it, LAYER_REFERENCE) }
        }

        val type = transactionTypeOf(parsed.direction)
        val timestampMillis = parsed.timestamp.toEpochMilli()
        val candidates = transactions.findByAccountAmountAndWindow(
            accountId = accountId,
            type = type,
            amountPaise = parsed.amountPaise,
            fromMillis = timestampMillis - weakWindow.toMillis(),
            toMillis = timestampMillis + weakWindow.toMillis(),
        ).filter { it.id.isNotEmpty() }

        val comparable = candidates.filter { candidate -> referencesAgree(candidate, parsed.referenceNumber) }

        parsed.balancePaise?.let { balance ->
            comparable.firstOrNull { it.balanceAfterPaise == balance }?.let { return Verdict.Duplicate(it, LAYER_BALANCE) }
        }

        if (parsed.referenceNumber != null || parsed.balancePaise != null) return Verdict.Unique

        val weak = comparable.firstOrNull() ?: return Verdict.Unique
        return if (sameChannel(weak.senderHeader, senderHeader)) {
            Verdict.PossibleDuplicate(weak)
        } else {
            Verdict.Duplicate(weak, LAYER_WEAK)
        }
    }

    private fun referencesAgree(candidate: Transaction, reference: String?): Boolean {
        val existing = candidate.referenceNumber ?: return reference == null
        val incoming = reference ?: return true
        return existing == incoming
    }

    private fun sameChannel(existingHeader: String?, incomingHeader: String): Boolean = existingHeader == incomingHeader

    const val LAYER_EXACT_REPEAT = 4
    const val LAYER_REFERENCE = 1
    const val LAYER_BALANCE = 2
    const val LAYER_WEAK = 3
}