package com.aditya.spent.sms

import com.aditya.spent.model.ParsedDirection
import com.aditya.spent.model.ParsedTransaction
import com.aditya.spent.model.RawSms
import com.aditya.spent.port.TransactionExtractor
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import java.time.Instant
import java.time.ZoneOffset

class ParserTest {
    private lateinit var parser: TransactionExtractor

    @Before
    fun setup() {
        parser = Parser
    }

    @Test fun parseIciciDebit1() {
        val sms = RawSms(
            "1", "ICICIT",
            "ICICI Bank Acct XX943 debited for Rs 340.00 on 03-Oct-26; ASHOKA ENTERPRI credited. UPI:626639807621. Call 18001662 for dispute. SMS BLOCK 958 to 9210976766.",
            1696204800000
        )
        val parsed = parser.extract(sms)
        assertNotNull(parsed)
        assertEquals(ParsedDirection.DEBIT, parsed!!.direction)
        assertEquals(34000L, parsed.amountPaise)
        assertEquals("943", parsed.accountDigits)
        assertEquals(Instant.parse("2026-10-03T00:00:00Z"), parsed.timestamp)
        assertEquals("ASHOKA ENTERPRI", parsed.merchant)
        assertEquals("626639807621", parsed.referenceNumber)
        assertNull(parsed.balancePaise)
        assertNull(parsed.availableBalancePaise)
    }

    @Test fun parseIciciDebit2() {
        val sms = RawSms(
            "2", "ICICIT",
            "ICICI Bank Acct XX848 debited for Rs 125.00 on 03-Oct-26; Shri Sai super credited. UPI:347627280987. Call 18872662 for dispute. SMS BLOCK 908 to 9209876746.",
            1696204860000
        )
        val parsed = parser.extract(sms)
        assertNotNull(parsed)
        assertEquals(ParsedDirection.DEBIT, parsed!!.direction)
        assertEquals(12500L, parsed.amountPaise)
        assertEquals("848", parsed.accountDigits)
        assertEquals(Instant.parse("2026-10-03T00:00:00Z"), parsed.timestamp)
        assertEquals("Shri Sai super", parsed.merchant)
        assertEquals("347627280987", parsed.referenceNumber)
        assertNull(parsed.balancePaise)
        assertNull(parsed.availableBalancePaise)
    }

    @Test fun parseIciciCredit() {
        val sms = RawSms(
            "3", "ICICIT",
            "Dear Customer, Acct XX658 is credited with Rs 2728.00 on 02-Oct-26 from SURYAA VISHAL J. UPI:601125936107-ICICI Bank.",
            1696118400000
        )
        val parsed = parser.extract(sms)
        assertNotNull(parsed)
        assertEquals(ParsedDirection.CREDIT, parsed!!.direction)
        assertEquals(272800L, parsed.amountPaise)
        assertEquals("658", parsed.accountDigits)
        assertEquals(Instant.parse("2026-10-02T00:00:00Z"), parsed.timestamp)
        assertEquals("SURYAA VISHAL J.", parsed.merchant)
        assertEquals("601125936107", parsed.referenceNumber)
        assertNull(parsed.balancePaise)
        assertNull(parsed.availableBalancePaise)
    }

    @Test fun parseMahaCredit1() {
        val sms = RawSms(
            "4", "MAHABK",
            "A/c X7095 credited with Rs. 3300.00 on 30-Sep-26 from SWAAMI ATUL ROR RRN: 663456743272 -Bank of Maharashtra",
            1696032000000
        )
        val parsed = parser.extract(sms)
        assertNotNull(parsed)
        assertEquals(ParsedDirection.CREDIT, parsed!!.direction)
        assertEquals(330000L, parsed.amountPaise)
        assertEquals("7095", parsed.accountDigits)
        assertEquals(Instant.parse("2026-09-30T00:00:00Z"), parsed.timestamp)
        assertEquals("SWAAMI ATUL ROR", parsed.merchant)
        assertEquals("663456743272", parsed.referenceNumber)
        assertNull(parsed.balancePaise)
        assertNull(parsed.availableBalancePaise)
    }

    @Test fun parseMahaCredit2() {
        val sms = RawSms(
            "5", "MAHABK",
            "Your a/c xxxx 7535 has been credited by INR 617.50 on 01-OCT-2026 by APB Credit Through PFMS  A09720063E406. A/c Bal is INR 33,846.94 CR and AVL Bal is INR 18,096.94-Bank of Maharashtra",
            1696032000000
        )
        val parsed = parser.extract(sms)
        assertNotNull(parsed)
        assertEquals(ParsedDirection.CREDIT, parsed!!.direction)
        assertEquals(61750L, parsed.amountPaise)
        assertEquals("7535", parsed.accountDigits)
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), parsed.timestamp)
        assertEquals("APB Credit Through PFMS", parsed.merchant)
        assertEquals("A09720063E406", parsed.referenceNumber)
        assertEquals(3384694L, parsed.balancePaise)
        assertEquals(1809694L, parsed.availableBalancePaise)
    }

    @Test fun parseMahaDebit() {
        val sms = RawSms(
            "6", "MAHABK",
            "A/c X7095 debited by Rs. 1270.00 for UPI payment to SHRI GAJANAN IC on 02-Aug-26. RRN: 658030981525 if not you, call 18098378526 -Bank of Maharashtra",
            1693622400000
        )
        val parsed = parser.extract(sms)
        assertNotNull(parsed)
        assertEquals(ParsedDirection.DEBIT, parsed!!.direction)
        assertEquals(127000L, parsed.amountPaise)
        assertEquals("7095", parsed.accountDigits)
        assertEquals(Instant.parse("2026-08-02T00:00:00Z"), parsed.timestamp)
        assertEquals("SHRI GAJANAN IC", parsed.merchant)
        assertEquals("658030981525", parsed.referenceNumber)
        assertNull(parsed.balancePaise)
        assertNull(parsed.availableBalancePaise)
    }

    @Test fun parseUnparseableReturnsNull() {
        val sms = RawSms("7", "ICICIT", "This is not a transaction message", 1696204800000)
        val parsed = parser.extract(sms)
        assertNull(parsed)
    }
}