package com.aditya.spent.usecase

import com.aditya.spent.model.Transaction
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

    private fun transaction(type: TransactionType, amount: Long = 500L, destination: String? = null, category: String? = "c") = Transaction("t", type, amount, "a", destination, category, 0L)
}
