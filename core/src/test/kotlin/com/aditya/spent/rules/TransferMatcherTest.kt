package com.aditya.spent.rules

import com.aditya.spent.model.Transaction
import com.aditya.spent.model.TransactionSource
import com.aditya.spent.model.TransactionType
import com.aditya.spent.repository.FakeTransactionRepository
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class TransferMatcherTest {
    private lateinit var repository: FakeTransactionRepository

    private val at = Instant.parse("2026-10-03T00:00:00Z")

    @Before
    fun setUp() {
        repository = FakeTransactionRepository()
    }

    private fun leg(
        id: String,
        type: TransactionType,
        accountId: String,
        amountPaise: Long = 330000L,
        timestampMillis: Long = at.toEpochMilli(),
        transferId: String? = null,
    ) = Transaction(
        id = id,
        type = type,
        amountPaise = amountPaise,
        accountId = accountId,
        destinationAccountId = null,
        categoryId = null,
        timestampMillis = timestampMillis,
        counterparty = null,
        referenceNumber = null,
        note = null,
        source = TransactionSource.SMS,
        originalSmsId = id,
        balanceAfterPaise = null,
        senderHeader = "MAHABK",
        smsHash = null,
        needsReview = false,
        transferId = transferId,
    )

    private val newLeg = leg("new", TransactionType.EXPENSE, "acc-a")

    private suspend fun evaluate(leg: Transaction = newLeg, accountIds: Set<String> = setOf("acc-a", "acc-b")) =
        TransferMatcher.evaluate(leg, accountIds, repository)

    @Test
    fun oppositeLegOnAnotherAccountIsMatched() = runBlocking<Unit> {
        repository.setTransactions(listOf(leg("old", TransactionType.INCOME, "acc-b")))
        val matched = assertIs<TransferMatcher.Verdict.Matched>(evaluate())
        assertEquals("old", matched.existingLeg.id)
    }

    @Test
    fun matchedTransferPointsFromDebitToCreditAccount() = runBlocking<Unit> {
        repository.setTransactions(listOf(leg("old", TransactionType.INCOME, "acc-b")))
        val matched = assertIs<TransferMatcher.Verdict.Matched>(evaluate())
        assertEquals(TransactionType.TRANSFER, matched.transfer.type)
        assertEquals("acc-a", matched.transfer.accountId)
        assertEquals("acc-b", matched.transfer.destinationAccountId)
        assertEquals(330000L, matched.transfer.amountPaise)
        assertNull(matched.transfer.categoryId)
    }

    @Test
    fun incomingCreditLegProducesTheSameDirectionOfFlow() = runBlocking<Unit> {
        repository.setTransactions(listOf(leg("old", TransactionType.EXPENSE, "acc-b")))
        val matched = assertIs<TransferMatcher.Verdict.Matched>(
            evaluate(leg("new", TransactionType.INCOME, "acc-a")),
        )
        assertEquals("acc-b", matched.transfer.accountId)
        assertEquals("acc-a", matched.transfer.destinationAccountId)
    }

    @Test
    fun twoCandidatesAreAmbiguous() = runBlocking<Unit> {
        repository.setTransactions(listOf(
            leg("old-1", TransactionType.INCOME, "acc-b"),
            leg("old-2", TransactionType.INCOME, "acc-c"),
        ))
        val ambiguous = assertIs<TransferMatcher.Verdict.Ambiguous>(
            evaluate(accountIds = setOf("acc-a", "acc-b", "acc-c")),
        )
        assertEquals(2, ambiguous.candidateCount)
    }

    @Test
    fun noOppositeLegIsNone() = runBlocking<Unit> {
        repository.setTransactions(listOf(leg("old", TransactionType.EXPENSE, "acc-b")))
        assertIs<TransferMatcher.Verdict.None>(evaluate())
    }

    @Test
    fun sameAccountOppositeLegIsNotATransfer() = runBlocking<Unit> {
        repository.setTransactions(listOf(leg("old", TransactionType.INCOME, "acc-a")))
        assertIs<TransferMatcher.Verdict.None>(evaluate())
    }

    @Test
    fun amountOutsideTheWindowIsNotMatched() = runBlocking<Unit> {
        repository.setTransactions(listOf(
            leg("old", TransactionType.INCOME, "acc-b", timestampMillis = at.minusSeconds(200_000).toEpochMilli()),
        ))
        assertIs<TransferMatcher.Verdict.None>(evaluate())
    }

    @Test
    fun legsAlreadyInATransferAreNotReused() = runBlocking<Unit> {
        repository.setTransactions(listOf(leg("old", TransactionType.INCOME, "acc-b", transferId = "tr-1")))
        assertIs<TransferMatcher.Verdict.None>(evaluate())
    }

    @Test
    fun unknownAccountIdsCannotMatch() = runBlocking<Unit> {
        repository.setTransactions(listOf(leg("old", TransactionType.INCOME, "acc-zzz")))
        assertIs<TransferMatcher.Verdict.None>(evaluate())
    }

    @Test
    fun aTransferRowItselfNeverMatches() = runBlocking<Unit> {
        repository.setTransactions(listOf(leg("old", TransactionType.INCOME, "acc-b")))
        val transferLeg = leg("new", TransactionType.TRANSFER, "acc-a")
        assertIs<TransferMatcher.Verdict.None>(evaluate(transferLeg))
    }
}