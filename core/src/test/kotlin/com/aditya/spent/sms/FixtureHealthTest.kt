package com.aditya.spent.sms

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FixtureHealthTest {

    private val detector = TransactionDetector

    @Test
    fun fixtureHealthSummary() {
        val messages = Fixtures.loadAll()
        val directory = Fixtures.smsDirectory()
        println("=== PARSER HEALTH (ARCHITECTURE section 15) ===")
        println("fixture directory: ${directory?.absolutePath ?: "NOT FOUND"}")
        println("messages read: ${messages.size}")
        assertTrue(messages.isNotEmpty(), "no fixture messages were loaded")

        var transactions = 0
        var fullParse = 0
        var missingDigits = 0
        var missingReference = 0
        var missingCounterparty = 0
        val dropped = mutableListOf<String>()
        val queuedForReview = mutableListOf<String>()
        val incompleteList = mutableListOf<String>()

        for (message in messages) {
            val sender = SenderNormalizer.normalize(message.sender)
            val readable = SenderPreFilter.evaluate(sender, userIgnored = false) is SenderPreFilter.Verdict.Read
            if (!readable) {
                dropped += "${message.sender}: ${message.body.take(70)}"
                continue
            }
            if (!detector.isTransactionMessage(message.body)) {
                if (detector.looksFinancialButUnparsed(message.body)) {
                    queuedForReview += "${message.sender}: ${message.body.take(70)}"
                } else {
                    dropped += "${message.sender}: ${message.body.take(70)}"
                }
                continue
            }
            transactions++
            val parsed = Parser.extract(message)
            if (parsed == null) {
                incompleteList += "${message.sender}: ${message.body.take(70)}"
                continue
            }
            var complete = true
            if (parsed.accountDigits.isNullOrBlank()) {
                missingDigits++
                complete = false
                incompleteList += "${message.sender}: ${message.body.take(70)}"
            }
            if (parsed.referenceNumber.isNullOrBlank()) missingReference++
            if (parsed.merchant.isNullOrBlank()) missingCounterparty++
            if (complete) fullParse++
        }

        println("looks like a transaction: $transactions")
        println("fully parsed: $fullParse")
        println("parsed but incomplete (missing account digits): $missingDigits")
        println("missing reference (informational): $missingReference")
        println("missing counterparty (informational): $missingCounterparty")
        println("queued for review (unreadable amount): ${queuedForReview.size}")
        queuedForReview.forEach { println("  queue $it") }
        println("dropped as not a transaction: ${dropped.size}")
        dropped.forEach { println("  drop  $it") }
        incompleteList.forEach { println("  incomplete $it") }
        println("=== END PARSER HEALTH ===")
    }

    @Test
    fun healthDirectoryIsFound() {
        assertTrue(Fixtures.smsDirectory()?.isDirectory == true, "fixture directory not found")
        assertFalse(Fixtures.loadAll().isEmpty(), "fixture corpus is empty")
    }

    @Test
    fun everyFixtureSenderIsNormalizable() {
        val messages = Fixtures.loadAll()
        assertTrue(messages.isNotEmpty())
        messages.forEach { message ->
            assertTrue(SenderPreFilter.isAlphanumericHeader(message.sender), "sender not alphanumeric: ${message.sender}")
            assertTrue(SenderNormalizer.normalize(message.sender).header.isNotBlank())
        }
    }

    @Test
    fun referenceIsNeverBlankWhenExtracted() {
        Fixtures.loadAll().forEach { message ->
            val reference = Parser.extract(message)?.referenceNumber
            if (reference != null) assertTrue(reference.isNotBlank(), "blank reference for ${message.id}")
        }
    }

    @Test
    fun accountDigitsAreThreeOrFourCharacters() {
        Fixtures.loadAll().forEach { message ->
            val digits = Parser.extract(message)?.accountDigits
            if (digits != null) {
                assertTrue(digits.length in 3..4, "unexpected digit count '$digits' in ${message.id}")
                assertTrue(digits.all { it.isDigit() }, "non-digit account digits '$digits' in ${message.id}")
            }
        }
    }

    @Test
    fun counterpartyNeverStartsWithBalanceFragment() {
        Fixtures.loadAll().forEach { message ->
            val merchant = Parser.extract(message)?.merchant
            if (merchant != null) {
                assertFalse(merchant.startsWith("Bal", ignoreCase = true), "balance fragment as merchant in ${message.id}")
            }
        }
    }

    @Test
    fun detectedMessagesAlwaysParse() {
        Fixtures.loadAll().forEach { message ->
            if (!detector.isTransactionMessage(message.body)) return@forEach
            val parsed = Parser.extract(message)
            assertNotNull(parsed, "detected as a transaction but not parsed: ${message.id}")
            assertTrue(parsed!!.amountPaise > 0, "non-positive amount in ${message.id}")
        }
    }
}