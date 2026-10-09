package com.aditya.spent.rules

import com.aditya.spent.model.Transaction
import com.aditya.spent.model.TransactionType
import com.aditya.spent.repository.TransactionRepository
import java.time.Duration
import java.util.UUID

object TransferMatcher {

    sealed interface Verdict {
        object None : Verdict
        data class Matched(val existingLeg: Transaction, val transfer: Transaction) : Verdict
        data class Ambiguous(val candidateCount: Int) : Verdict
    }

    suspend fun evaluate(
        newLeg: Transaction,
        accountIds: Set<String>,
        transactions: TransactionRepository,
        matchWindow: Duration = Constants.TRANSFER_MATCH_WINDOW,
    ): Verdict {
        if (newLeg.type == TransactionType.TRANSFER) return Verdict.None
        val oppositeType = when (newLeg.type) {
            TransactionType.INCOME -> TransactionType.EXPENSE
            TransactionType.EXPENSE -> TransactionType.INCOME
            TransactionType.TRANSFER -> return Verdict.None
        }
        val timestampMillis = newLeg.timestampMillis
        val candidates = accountIds
            .filter { it != newLeg.accountId }
            .flatMap { accountId ->
                transactions.findByAccountAmountAndWindow(
                    accountId = accountId,
                    type = oppositeType,
                    amountPaise = newLeg.amountPaise,
                    fromMillis = timestampMillis - matchWindow.toMillis(),
                    toMillis = timestampMillis + matchWindow.toMillis(),
                )
            }
            .filter { it.transferId == null }

        return when (candidates.size) {
            0 -> Verdict.None
            1 -> Verdict.Matched(candidates.first(), transferFor(newLeg, candidates.first()))
            else -> Verdict.Ambiguous(candidates.size)
        }
    }

    private fun transferFor(debitLeg: Transaction, creditLeg: Transaction): Transaction {
        val (source, destination) = when {
            debitLeg.type == TransactionType.EXPENSE -> debitLeg to creditLeg
            else -> creditLeg to debitLeg
        }
        return Transaction(
            id = UUID.randomUUID().toString(),
            type = TransactionType.TRANSFER,
            amountPaise = debitLeg.amountPaise,
            accountId = source.accountId,
            destinationAccountId = destination.accountId,
            timestampMillis = minOf(debitLeg.timestampMillis, creditLeg.timestampMillis),
            source = debitLeg.source,
        )
    }
}