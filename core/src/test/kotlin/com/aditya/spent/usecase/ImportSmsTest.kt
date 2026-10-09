package com.aditya.spent.usecase

import com.aditya.spent.model.Account
import com.aditya.spent.model.ParsedDirection
import com.aditya.spent.model.ParsedTransaction
import com.aditya.spent.model.RawSms
import com.aditya.spent.model.ReviewReason
import com.aditya.spent.model.Transaction
import com.aditya.spent.model.TransactionSource
import com.aditya.spent.model.TransactionType
import com.aditya.spent.port.Clock
import com.aditya.spent.port.FakeClock
import com.aditya.spent.port.FakeSmsSource
import com.aditya.spent.port.SmsSource
import com.aditya.spent.repository.FakeAccountRepository
import com.aditya.spent.repository.FakeTransactionRepository
import com.aditya.spent.repository.FakeReviewQueueRepository
import com.aditya.spent.repository.FakeSenderMappingRepository
import com.aditya.spent.sms.Parser
import com.aditya.spent.sms.SenderRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.UUID

class ImportSmsTest {
    private lateinit var smsSource: FakeSmsSource
    private lateinit var clock: FakeClock
    private lateinit var accountRepo: FakeAccountRepository
    private lateinit var transactionRepo: FakeTransactionRepository
    private lateinit var reviewQueue: FakeReviewQueueRepository
    private lateinit var senderMappings: FakeSenderMappingRepository
    private lateinit var ingestSms: IngestSms
    private lateinit var importSms: ImportSms

    @Before
    fun setup() {
        clock = FakeClock(Instant.parse("2026-10-03T00:00:00Z"))
        accountRepo = FakeAccountRepository()
        transactionRepo = FakeTransactionRepository()
        reviewQueue = FakeReviewQueueRepository()
        senderMappings = FakeSenderMappingRepository()

        // D-015: the user stores four digits; a message matches when its visible
        // digits are the end of those four (minimum three). ICICI shows three
        // digits, so each stored number ends with the digits the message shows.
        accountRepo.setAccounts(listOf(
            Account("acc-icici-943", "ICICI Savings", "ICICI", "Savings", "2943", 0, 0),
            Account("acc-icici-848", "ICICI Salary", "ICICI", "Savings", "2848", 0, 0),
            Account("acc-icici-658", "ICICI FD", "ICICI", "Savings", "2658", 0, 0),
            Account("acc-maha-7095", "Maharashtra Savings", "MAHABK", "Savings", "7095", 0, 0),
            Account("acc-maha-7535", "Maharashtra PFMS", "MAHABK", "Savings", "7535", 0, 0),
            Account("acc-maha-7435", "Mah Maharashtra Demat", "MAHABK", "Savings", "7435", 0, 0),
        ))

        // Authoritative test messages (exact from spec table)
        val fixtureMessages = listOf(
            RawSms("sms-1", "ICICIT",
                "ICICI Bank Acct XX943 debited for Rs 340.00 on 03-Oct-26; ASHOKA ENTERPRI credited. UPI:626639807621. Call 18001662 for dispute. SMS BLOCK 958 to 9210976766.",
                1696204800000),
            RawSms("sms-2", "ICICIT",
                "ICICI Bank Acct XX848 debited for Rs 125.00 on 03-Oct-26; Shri Sai super credited. UPI:347627280987. Call 18872662 for dispute. SMS BLOCK 908 to 9209876746.",
                1696204860000),
            RawSms("sms-3", "ICICIT",
                "Dear Customer, Acct XX658 is credited with Rs 2728.00 on 02-Oct-26 from SURYAA VISHAL J. UPI:601125936107-ICICI Bank.",
                1696118400000),
            RawSms("sms-4", "MAHABK",
                "A/c X7095 credited with Rs. 3300.00 on 30-Sep-26 from SWAAMI ATUL ROR RRN: 663456743272 -Bank of Maharashtra",
                1696032000000),
            RawSms("sms-5", "MAHABK",
                "Your a/c xxxx 7535 has been credited by INR 617.50 on 01-OCT-2026 by APB Credit Through PFMS  A09720063E406. A/c Bal is INR 33,846.94 CR and AVL Bal is INR 18,096.94-Bank of Maharashtra",
                1696032000000),
            RawSms("sms-6", "MAHABK",
                "A/c X7095 debited by Rs. 1270.00 for UPI payment to SHRI GAJANAN IC on 02-Aug-26. RRN: 658030981525 if not you, call 18098378526 -Bank of Maharashtra",
                1693622400000),
        )
        smsSource = FakeSmsSource(fixtureMessages)

        ingestSms = IngestSms(
            extractor = Parser,
            registry = SenderRegistry.bundled(),
            clock = clock,
            accountRepository = accountRepo,
            transactionRepository = transactionRepo,
            reviewQueue = reviewQueue,
            senderMappings = senderMappings,
        )
        importSms = ImportSms(smsSource, ingestSms)
    }

    private fun importerFor(source: SmsSource) = ImportSms(source, ingestSms)

    @Test
    fun importAllFixturesCreatesCorrectTransactions() = runBlocking {
        val results = importSms(0L).toList()

        val successes = results.filterIsInstance<IngestResult.Success>()
        assert(successes.size == 6) { "Expected 6 successful imports, got ${successes.size}" }

        val transactions = transactionRepo.observeTransactions().first()
        assert(transactions.size == 6) { "Expected 6 transactions in repo, got ${transactions.size}" }

        // Verify amounts and types
        val debit340 = transactions.find { it.amountPaise == 34000L && it.type == TransactionType.EXPENSE }
        assert(debit340 != null) { "Missing debit 340.00" }
        assert(debit340!!.counterparty == "ASHOKA ENTERPRI") { "Wrong merchant for debit 340, got ${debit340.counterparty}" }
        assert(debit340!!.accountId == "acc-icici-943") { "Wrong account for debit 340" }

        val debit125 = transactions.find { it.amountPaise == 12500L && it.type == TransactionType.EXPENSE }
        assert(debit125 != null) { "Missing debit 125.00" }
        assert(debit125!!.counterparty == "Shri Sai super") { "Wrong merchant for debit 125" }

        val credit2728 = transactions.find { it.amountPaise == 272800L && it.type == TransactionType.INCOME }
        assert(credit2728 != null) { "Missing credit 2728.00" }
        assert(credit2728!!.counterparty == "SURYAA VISHAL J.") { "Wrong merchant for credit 2728" }

        val credit3300 = transactions.find { it.amountPaise == 330000L && it.type == TransactionType.INCOME }
        assert(credit3300 != null) { "Missing credit 3300.00" }
        assert(credit3300!!.counterparty == "SWAAMI ATUL ROR") { "Wrong merchant for credit 3300" }

        val credit617 = transactions.find { it.amountPaise == 61750L && it.type == TransactionType.INCOME }
        assert(credit617 != null) { "Missing credit 617.50" }
        assert(credit617!!.counterparty == "APB Credit Through PFMS") { "Wrong merchant for credit 617" }

        val debit1270 = transactions.find { it.amountPaise == 127000L && it.type == TransactionType.EXPENSE }
        assert(debit1270 != null) { "Missing debit 1270.00" }
        assert(debit1270!!.counterparty == "SHRI GAJANAN IC") { "Wrong merchant for debit 1270" }
    }

    @Test
    fun reImportSameFixturesCreatesNoDuplicates() = runBlocking {
        // First import
        importSms(0L).toList()
        val firstCount = transactionRepo.observeTransactions().first().size

        // Second import (same messages)
        importSms(0L).toList()
        val secondCount = transactionRepo.observeTransactions().first().size

        assert(firstCount == secondCount) { "Re-import created duplicates: $firstCount -> $secondCount" }
    }

    @Test
    fun nonTransactionContentIsDroppedNotQueued() = runBlocking {
        val junkSms = FakeSmsSource(listOf(
            RawSms("sms-junk", "ICICIT", "This is not a transaction message", 1696204800000)
        ))
        val results = importerFor(junkSms)(0L).toList()

        assert(results.size == 1) { "Expected one result per message" }
        val dropped = results[0]
        assert(dropped is IngestResult.Dropped && dropped.reason == DropReason.NOT_A_TRANSACTION) {
            "Junk should be dropped, got $dropped"
        }
        assert(reviewQueue.observeAll().first().isEmpty()) { "Junk must not reach the review queue" }
        assert(transactionRepo.observeTransactions().first().isEmpty()) { "Junk must not create a transaction" }
    }

    @Test
    fun unknownSenderWithTransactionContentGoesToReviewQueue() = runBlocking {
        val unknownSms = FakeSmsSource(listOf(
            RawSms("sms-unknown", "ZZZTXNBK", "A/c XX4321 debited for Rs 100.00 on 03-Oct-26", 1696204800000)
        ))
        val results = importerFor(unknownSms)(0L).toList()

        val reviewItems = reviewQueue.observeAll().first()
        assert(reviewItems.size == 1) { "Expected 1 review item" }
        assert(reviewItems[0].reason == ReviewReason.UNKNOWN_SENDER) { "Wrong review reason" }
        assert(results.single() is IngestResult.ReviewQueued) { "Wrong result $results" }
    }

    @Test
    fun parsedButUnassignedMessageGoesToReviewQueue() = runBlocking {
        val unknownAccountSms = FakeSmsSource(listOf(
            RawSms("sms-unassigned", "ICICIT", "ICICI Bank Acct XX4321 debited for Rs 100.00 on 03-Oct-26", 1696204800000)
        ))
        importerFor(unknownAccountSms)(0L).toList()

        val reviewItems = reviewQueue.observeAll().first()
        assert(reviewItems.size == 1) { "Expected 1 review item" }
        assert(reviewItems[0].reason == ReviewReason.NO_ACCOUNT_MATCH) { "Wrong review reason" }
    }

    @Test
    fun extractorFailureGoesToReviewQueueAsUnparseable() = runBlocking {
        val stubExtractor = object : com.aditya.spent.port.TransactionExtractor {
            override fun extract(rawSms: RawSms): ParsedTransaction? = null
        }
        val stubIngest = IngestSms(
            extractor = stubExtractor,
            registry = SenderRegistry.bundled(),
            clock = clock,
            accountRepository = accountRepo,
            transactionRepository = transactionRepo,
            reviewQueue = reviewQueue,
            senderMappings = FakeSenderMappingRepository(),
        )
        val source = FakeSmsSource(listOf(
            RawSms("sms-stub", "ICICIT", "ICICI Bank Acct XX943 debited for Rs 340.00 on 03-Oct-26", 1696204800000)
        ))
        val results = ImportSms(source, stubIngest)(0L).toList()

        val reviewItems = reviewQueue.observeAll().first()
        assert(reviewItems.size == 1) { "Expected 1 review item" }
        assert(reviewItems[0].reason == ReviewReason.UNPARSEABLE) { "Wrong review reason" }
        assert(reviewItems[0].rawSms.id == "sms-stub") { "Review item lost the original SMS" }
        assert(results.single() is IngestResult.ReviewQueued) { "Wrong result $results" }
    }

    @Test
    fun walletTopUpIsImportedAsAnExpense() = runBlocking {
        // The message carries no account digits, so D-011 resolves it through a
        // sender mapping the user made once.
        senderMappings.mapSenderToAccount("MAHABK", "acc-maha-7095")
        val topUpSms = FakeSmsSource(listOf(
            RawSms(
                "sms-topup", "AX-MAHABK-S",
                "UPI LITE Top-up amounting to Rs. 474.06  has been successful. RRN : 636083778353 - Bank of Maharashtra",
                1696032000000,
            ),
        ))
        val results = importerFor(topUpSms)(0L).toList()

        val success = results.filterIsInstance<IngestResult.Success>().single()
        assert(success.transaction.type == TransactionType.EXPENSE) { "Top-up must be an expense" }
        assert(success.transaction.amountPaise == 47406L) { "Wrong amount ${success.transaction.amountPaise}" }
        assert(success.transaction.accountId == "acc-maha-7095") { "Wrong account ${success.transaction.accountId}" }
        assert(success.transaction.referenceNumber == "636083778353") { "Wrong reference" }
        assert(success.transaction.senderHeader == "MAHABK") { "Wrong sender header" }
    }

    @Test
    fun asbaLienIsImportedAsAnExpense() = runBlocking {
        val lienSms = FakeSmsSource(listOf(
            RawSms(
                "sms-lien", "JM-MAHABK-S",
                "ASBA lien of Rs. 31,668.48 has been applied in your account No:xxxx7435  at 30-09-2026 02:09:45 -Bank of Maharashtra",
                1696032000000,
            ),
        ))
        val results = importerFor(lienSms)(0L).toList()

        val success = results.filterIsInstance<IngestResult.Success>().single()
        assert(success.transaction.type == TransactionType.EXPENSE) { "Lien must be an expense" }
        assert(success.transaction.amountPaise == 3166848L) { "Wrong amount ${success.transaction.amountPaise}" }
        assert(success.transaction.accountId == "acc-maha-7435") { "Wrong account ${success.transaction.accountId}" }
        assert(success.transaction.timestampMillis == 1790726400000L) { "Wrong date" }
    }

    @Test
    fun holdWithUnreadableAmountGoesToReviewInsteadOfBeingDropped() = runBlocking {
        val holdSms = FakeSmsSource(listOf(
            RawSms(
                "sms-hold", "JM-MAHABK-S",
                "Dear customer, A hold of INR          49621.723 has been applied on your A/C XXXXXXX7435Due to Sandeep Gaikwad     11-UPI            For queries,contact your Home Branch-Bank of Maharashtra.",
                1696032000000,
            ),
        ))
        val results = importerFor(holdSms)(0L).toList()

        val reviewItems = reviewQueue.observeAll().first()
        assert(reviewItems.size == 1) { "Expected 1 review item, got ${reviewItems.size}" }
        assert(reviewItems[0].reason == ReviewReason.UNPARSEABLE) { "Wrong reason ${reviewItems[0].reason}" }
        assert(results.single() is IngestResult.ReviewQueued) { "Wrong result $results" }
        assert(transactionRepo.observeTransactions().first().isEmpty()) { "No amount may be invented" }
    }

    @Test
    fun noAccountMatchGoesToReviewQueue() = runBlocking {
        // Account with last4 that doesn't exist in our test accounts
        val noMatchSms = FakeSmsSource(listOf(
            RawSms("sms-nomatch", "ICICIT", "ICICI Bank Acct XX9999 debited for Rs 100.00 on 03-Oct-26", 1696204800000)
        ))
        val testImport = importerFor(noMatchSms)

        testImport(0L).toList()

        val reviewItems = reviewQueue.observeAll().first()
        assert(reviewItems.size == 1) { "Expected 1 review item" }
        assert(reviewItems[0].reason == ReviewReason.NO_ACCOUNT_MATCH) { "Wrong review reason" }
    }
}