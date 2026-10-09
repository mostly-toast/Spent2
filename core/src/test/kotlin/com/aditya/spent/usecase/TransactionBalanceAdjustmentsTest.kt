package com.aditya.spent.usecase

import com.aditya.spent.model.Transaction
import com.aditya.spent.model.TransactionSource
import com.aditya.spent.model.TransactionType
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TransactionBalanceAdjustmentsTest {
    private val adjustments = TransactionBalanceAdjustments()

    @Test fun incomeCreditsItsAccount() = assertEquals(listOf(BalanceAdjustment("a", 500L)), adjustments(transaction(TransactionType.INCOME)))
    @Test fun expenseDebitsItsAccount() = assertEquals(listOf(BalanceAdjustment("a", -500L)), adjustments(transaction(TransactionType.EXPENSE)))
    @Test fun transferDebitsSourceAndCreditsDestination() = assertEquals(listOf(BalanceAdjustment("a", -500L), BalanceAdjustment("b", 500L)), adjustments(transaction(TransactionType.TRANSFER, destination = "b", category = null)))
    @Test fun transferRequiresDifferentAccounts() { assertFailsWith<IllegalArgumentException> { adjustments(transaction(TransactionType.TRANSFER, destination = "a", category = null)) } }
    @Test fun incomeAndExpenseRequireCategory() { assertFailsWith<IllegalArgumentException> { adjustments(transaction(TransactionType.EXPENSE, category = null)) } }
    @Test fun transferRejectsCategory() { assertFailsWith<IllegalArgumentException> { adjustments(transaction(TransactionType.TRANSFER, destination = "b")) } }
    @Test fun amountMustBePositive() { assertFailsWith<IllegalArgumentException> { adjustments(transaction(TransactionType.INCOME, amount = 0L)) } }

    // D-019: an SMS row may stay uncategorised until Phase 4 binning.
    @Test fun smsIncomeAndExpenseMayBeUncategorised() {
        assertEquals(listOf(BalanceAdjustment("a", 500L)), adjustments(transaction(TransactionType.INCOME, category = null, source = TransactionSource.SMS)))
        assertEquals(listOf(BalanceAdjustment("a", -500L)), adjustments(transaction(TransactionType.EXPENSE, category = null, source = TransactionSource.SMS)))
    }

    // D-021: an SMS transfer summary row carries no balance effect; its legs do.
    @Test fun detectedTransferSummaryHasNoBalanceEffect() {
        assertEquals(
            emptyList(),
            adjustments(transaction(TransactionType.TRANSFER, destination = "b", category = null, source = TransactionSource.SMS)),
        )
    }

    private fun transaction(
        type: TransactionType,
        amount: Long = 500L,
        destination: String? = null,
        category: String? = "c",
        source: TransactionSource = TransactionSource.MANUAL,
    ) = Transaction("t", type, amount, "a", destination, category, 0L, source = source)
}
