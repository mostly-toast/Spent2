package com.aditya.spent.rules

import com.aditya.spent.model.ParsedDirection
import com.aditya.spent.model.ParsedTransaction
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
import kotlin.test.assertTrue

class DedupTest {
    private lateinit var repository: FakeTransactionRepository

    private val at = Instant.parse("2026-10-03T00:00:00Z")

    @Before
    fun setUp() {
        repository = FakeTransactionRepository()
    }

    private fun parsed(
        amountPaise: Long,
        direction: ParsedDirection = ParsedDirection.DEBIT,
        reference: String? = null,
        balance: Long? = null,
        timestamp: Instant = at,
    ) = ParsedTransaction(
        amountPaise = amountPaise,
        direction = direction,
        accountDigits = "2943",
        merchant = null,
        timestamp = timestamp,
        referenceNumber = reference,
        balancePaise = balance,
        availableBalancePaise = null,
        rawText = "body",
    )

    private fun stored(
        id: String,
        type: TransactionType = TransactionType.EXPENSE,
        amountPaise: Long = 34000L,
        accountId: String = "acc-a",
        reference: String? = null,
        balance: Long? = null,
        senderHeader: String? = "ICICIT",
        timestampMillis: Long = at.toEpochMilli(),
        transferId: String? = null,
        smsHash: String? = null,
    ) = Transaction(
        id = id,
        type = type,
        amountPaise = amountPaise,
        accountId = accountId,
        destinationAccountId = null,
        categoryId = null,
        timestampMillis = timestampMillis,
        counterparty = null,
        referenceNumber = reference,
        note = null,
        source = TransactionSource.SMS,
        originalSmsId = id,
        balanceAfterPaise = balance,
        senderHeader = senderHeader,
        smsHash = smsHash,
        needsReview = false,
        transferId = transferId,
    )

    private suspend fun evaluate(
        parsed: ParsedTransaction,
        senderHeader: String = "ICICIT",
        smsHash: String = "hash-1",
        accountId: String = "acc-a",
    ) = Dedup.evaluate(parsed, accountId, senderHeader, smsHash, repository)

    @Test
    fun exactRepeatIsADuplicate() = runBlocking<Unit> {
        repository.setTransactions(listOf(stored("t1", balance = 100000L, smsHash = "hash-1")))
        val verdict = evaluate(parsed(34000L, balance = 100000L))
        val duplicate = assertIs<Dedup.Verdict.Duplicate>(verdict)
        assertEquals(Dedup.LAYER_EXACT_REPEAT, duplicate.layer)
        assertEquals("t1", duplicate.existing.id)
    }

    @Test
    fun referenceMatchIsADuplicate() = runBlocking<Unit> {
        repository.setTransactions(listOf(stored("t1", reference = "626639807621")))
        val verdict = evaluate(parsed(34000L, reference = "626639807621"))
        val duplicate = assertIs<Dedup.Verdict.Duplicate>(verdict)
        assertEquals(Dedup.LAYER_REFERENCE, duplicate.layer)
    }

    @Test
    fun balanceMatchIsADuplicate() = runBlocking<Unit> {
        repository.setTransactions(listOf(stored("t1", balance = 100000L)))
        val verdict = evaluate(parsed(34000L, balance = 100000L))
        val duplicate = assertIs<Dedup.Verdict.Duplicate>(verdict)
        assertEquals(Dedup.LAYER_BALANCE, duplicate.layer)
    }

    @Test
    fun balanceMatchMustBeOnTheSameAccountAndAmount() = runBlocking<Unit> {
        repository.setTransactions(listOf(stored("t1", balance = 100000L)))
        val verdict = evaluate(parsed(12500L, balance = 100000L))
        assertIs<Dedup.Verdict.Unique>(verdict)
    }

    @Test
    fun referenceDisagreementBlocksTheWeakLayer() = runBlocking<Unit> {
        repository.setTransactions(listOf(stored("t1", reference = "626639807621")))
        val verdict = evaluate(parsed(34000L, reference = "347627280987"))
        assertIs<Dedup.Verdict.Unique>(verdict)
    }

    @Test
    fun weakMatchFromAnotherChannelIsADuplicate() = runBlocking<Unit> {
        repository.setTransactions(listOf(stored("t1", senderHeader = "MAHABK")))
        val verdict = evaluate(parsed(34000L), senderHeader = "ICICIT")
        val duplicate = assertIs<Dedup.Verdict.Duplicate>(verdict)
        assertEquals(Dedup.LAYER_WEAK, duplicate.layer)
    }

    @Test
    fun weakMatchFromTheSameChannelIsOnlyPossible() = runBlocking<Unit> {
        repository.setTransactions(listOf(stored("t1", senderHeader = "ICICIT")))
        val verdict = evaluate(parsed(34000L), senderHeader = "ICICIT")
        val possible = assertIs<Dedup.Verdict.PossibleDuplicate>(verdict)
        assertEquals("t1", possible.existing.id)
    }

    @Test
    fun weakMatchOutsideTheWindowIsUnique() = runBlocking<Unit> {
        repository.setTransactions(listOf(stored("t1", timestampMillis = at.minusSeconds(86_400).toEpochMilli())))
        assertIs<Dedup.Verdict.Unique>(evaluate(parsed(34000L)))
    }

    @Test
    fun nothingStoredIsUnique() = runBlocking<Unit> {
        assertIs<Dedup.Verdict.Unique>(evaluate(parsed(34000L)))
    }

    @Test
    fun oppositeDirectionOnTheSameAmountIsNotADuplicate() = runBlocking<Unit> {
        repository.setTransactions(listOf(stored("t1", type = TransactionType.INCOME)))
        assertIs<Dedup.Verdict.Unique>(evaluate(parsed(34000L, direction = ParsedDirection.DEBIT)))
    }

    @Test
    fun aLegInsideATransferStillDedupsAgainstItsOwnMessage() = runBlocking<Unit> {
        repository.setTransactions(listOf(stored("t1", senderHeader = "MAHABK", transferId = "tr-1")))
        val verdict = evaluate(parsed(34000L), senderHeader = "ICICIT")
        assertIs<Dedup.Verdict.Duplicate>(verdict)
    }

    @Test
    fun hashIsStableAndSenderSensitive() {
        val first = Dedup.computeSmsHash("ICICIT", "Rs 340.00 debited")
        assertEquals(first, Dedup.computeSmsHash("ICICIT", "Rs 340.00 debited"))
        assertEquals(64, first.length)
        assertTrue(first != Dedup.computeSmsHash("MAHABK", "Rs 340.00 debited"))
        assertTrue(first != Dedup.computeSmsHash("ICICIT", "Rs 340.01 debited"))
    }

    @Test
    fun directionMapsToTransactionType() {
        assertEquals(TransactionType.EXPENSE, Dedup.transactionTypeOf(ParsedDirection.DEBIT))
        assertEquals(TransactionType.INCOME, Dedup.transactionTypeOf(ParsedDirection.CREDIT))
    }
}