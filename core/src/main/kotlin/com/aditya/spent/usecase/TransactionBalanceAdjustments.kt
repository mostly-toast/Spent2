package com.aditya.spent.usecase

import com.aditya.spent.model.Transaction
import com.aditya.spent.model.TransactionSource
import com.aditya.spent.model.TransactionType

data class BalanceAdjustment(val accountId: String, val deltaPaise: Long)

class TransactionBalanceAdjustments {
    operator fun invoke(transaction: Transaction): List<BalanceAdjustment> {
        require(transaction.amountPaise > 0) { "Amount must be positive" }
        val categoryRequired = transaction.source != TransactionSource.SMS
        return when (transaction.type) {
            TransactionType.INCOME -> {
                require(transaction.categoryId != null || !categoryRequired) { "Income requires a category" }
                require(transaction.destinationAccountId == null) { "Income has no destination account" }
                listOf(BalanceAdjustment(transaction.accountId, transaction.amountPaise))
            }
            TransactionType.EXPENSE -> {
                require(transaction.categoryId != null || !categoryRequired) { "Expense requires a category" }
                require(transaction.destinationAccountId == null) { "Expense has no destination account" }
                listOf(BalanceAdjustment(transaction.accountId, -transaction.amountPaise))
            }
            TransactionType.TRANSFER -> {
                require(transaction.categoryId == null) { "Transfer has no category" }
                val destination = requireNotNull(transaction.destinationAccountId) { "Transfer requires a destination account" }
                require(transaction.accountId != destination) { "Transfer accounts must differ" }
                if (transaction.source == TransactionSource.SMS) {
                    emptyList()
                } else {
                    listOf(
                        BalanceAdjustment(transaction.accountId, -transaction.amountPaise),
                        BalanceAdjustment(destination, transaction.amountPaise),
                    )
                }
            }
        }
    }
}
