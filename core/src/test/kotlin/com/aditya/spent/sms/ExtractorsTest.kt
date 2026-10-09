package com.aditya.spent.sms

import com.aditya.spent.model.ParsedDirection
import org.junit.Test
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ExtractorsTest {

    private val fallback = Instant.parse("2026-01-15T10:30:00Z")
    private val startOf = Instant.parse("2026-10-03T00:00:00Z")
    private val august = Instant.parse("2026-08-02T00:00:00Z")

    @Test
    fun amountsWithCommonPrefixes() {
        assertEquals(34000L, Extractors.amount("Rs 340.00 debited from A/c XX9430"))
        assertEquals(330000L, Extractors.amount("Rs. 3300.00 credited to A/c XX7095"))
        assertEquals(61750L, Extractors.amount("INR 617.50 debited from A/c XX7535"))
        assertEquals(3428344L, Extractors.amount("INR 34,283.44 debited from A/c X7095"))
    }

    @Test
    fun amountWithoutSpaceAfterRupees() {
        assertEquals(318400L, Extractors.amount("Rs3184 debited from your account"))
    }

    @Test
    fun truncatedAmountIsRejected() {
        assertNull(Extractors.amount("INR 49621.723 debited from A/c XX9430"))
    }

    @Test
    fun blockedAmountWordingIsDetectedOnItsOwn() {
        assertTrue(Extractors.mentionsBlockedAmount("ASBA lien of Rs. 31,668.48 has been applied in your account No:xxxx7435"))
        assertTrue(Extractors.mentionsBlockedAmount("A hold of INR 49621.723 has been applied on your A/C XXXXXXX7435"))
        assertFalse(Extractors.mentionsBlockedAmount("Your AutoPay mandate is successfully revoked towards Google for Rs 5218.59"))
        assertFalse(Extractors.mentionsBlockedAmount("A/c X8869 debited by Rs. 474.06 for UPI payment to Amit Verma K"))
    }

    @Test
    fun amountWithNoDecimals() {
        assertEquals(640000L, Extractors.amount("Rs. 6400 received"))
    }

    @Test
    fun amountAfterSentencePeriodIsStillRead() {
        assertEquals(2500080L, Extractors.amount("The Avl Bal Rs. 25,000.80."))
        assertEquals(2500080L, Extractors.availableBalance("The Avl Bal Rs. 25,000.80."))
    }

    @Test
    fun directionWords() {
        assertEquals(ParsedDirection.DEBIT, Extractors.direction("Rs 340.00 debited from A/c XX9430"))
        assertEquals(ParsedDirection.DEBIT, Extractors.direction("Rs 340.00 spent at STORE"))
        assertEquals(ParsedDirection.CREDIT, Extractors.direction("Rs 3300.00 credited to A/c XX7095"))
        assertEquals(ParsedDirection.CREDIT, Extractors.direction("Rs 3300.00 received in your account"))
    }

    @Test
    fun negatedDirectionIsNotADirection() {
        assertNull(Extractors.direction("PMJJBY/PMSBY premium was not debited from your account"))
        assertNull(Extractors.direction("Your payment was never credited"))
    }

    @Test
    fun negationDoesNotHideAnotherUnnegatedOccurrence() {
        assertEquals(ParsedDirection.DEBIT, Extractors.direction("not debited earlier but Rs 340.00 debited now"))
    }

    @Test
    fun directionWordsAreNotMatchedInsideLongerWords() {
        assertNull(Extractors.direction("Your loan was repaid early"))
    }

    @Test
    fun walletTopUpIsADebit() {
        val body = "UPI LITE Top-up amounting to Rs. 474.06  has been successful. RRN : 636083778353 - Bank of Maharashtra"
        assertEquals(ParsedDirection.DEBIT, Extractors.direction(body))
        assertEquals(47406L, Extractors.amount(body))
        assertEquals("636083778353", Extractors.reference(body))
    }

    @Test
    fun asbaLienIsADebit() {
        val body = "ASBA lien of Rs. 31,668.48 has been applied in your account No:xxxx7435  at 30-09-2026 02:09:45 -Bank of Maharashtra"
        assertEquals(ParsedDirection.DEBIT, Extractors.direction(body))
        assertEquals(3166848L, Extractors.amount(body))
        assertEquals("7435", Extractors.accountDigits(body))
    }

    @Test
    fun appliedHoldIsADebitButItsTruncatedAmountIsNotRead() {
        val body = "Dear customer, A hold of INR          49621.723 has been applied on your A/C XXXXXXX7435Due to Sandeep Gaikwad     11-UPI            For queries,contact your Home Branch-Bank of Maharashtra."
        assertEquals(ParsedDirection.DEBIT, Extractors.direction(body))
        assertEquals("7435", Extractors.accountDigits(body))
        assertNull(Extractors.amount(body))
    }

    @Test
    fun transactionLimitWordingIsNotADirection() {
        assertNull(Extractors.direction("Per day cooling period limit for UPI transactions is Rs 3184 via WA for 72 hours"))
        assertNull(Extractors.direction("your total transaction limit is Rs3184 for 24 hours"))
    }

    @Test
    fun noDirectionWordGivesNoDirection() {
        assertNull(Extractors.direction("Your A/c XX9430 statement is ready"))
    }

    @Test
    fun accountDigitsPreferTheExplicitAccount() {
        assertEquals("9430", Extractors.accountDigits("A/c XX9430 debited Rs 340.00"))
        assertEquals("943", Extractors.accountDigits("A/c XX943 debited Rs 340.00"))
        assertEquals("7095", Extractors.accountDigits("From Acct No. 7095: INR 1270.00 debited"))
    }

    @Test
    fun maskedDigitsWithoutKeywordAreStillFound() {
        assertEquals("9430", Extractors.accountDigits("Rs 340.00 debited to XX9430"))
    }

    @Test
    fun noAccountInBodyGivesNoDigits() {
        assertNull(Extractors.accountDigits("Rs 340.00 debited from your account"))
    }

    @Test
    fun fourOrMoreDigitNumbersAreNotAccountDigits() {
        assertNull(Extractors.accountDigits("A/c X12345 debited Rs 340.00"))
    }

    @Test
    fun dateAfterOn() {
        assertEquals(startOf, Extractors.date("A/c XX9430 debited Rs 340.00 on 03-Oct-26", fallback))
        assertEquals(startOf, Extractors.date("A/c XX9430 debited Rs 340.00 on 03-Oct-2026", fallback))
    }

    @Test
    fun dateWithoutOnPrefix() {
        assertEquals(startOf, Extractors.date("Statement for 03-Oct-2026", fallback))
        assertEquals(startOf, Extractors.date("Dated 03.10.2026", fallback))
        assertEquals(startOf, Extractors.date("Dated 03/10/2026", fallback))
    }

    @Test
    fun dateWithoutYearUsesTheSmsYear() {
        assertEquals(august, Extractors.date("Dated 02-Aug-26 for A/c XX7095", fallback))
    }

    @Test
    fun missingDateFallsBackToTheSmsTimestamp() {
        assertEquals(fallback, Extractors.date("Rs 340.00 debited from A/c XX9430", fallback))
    }

    @Test
    fun referenceVariants() {
        assertEquals("40382640461", Extractors.reference("UPI: 40382640461 debited from A/c XX9430"))
        assertEquals("40422933951", Extractors.reference("RRN 40422933951 credited to A/c XX7095"))
    }

    @Test
    fun longAlphanumericTokenIsAReference() {
        assertEquals("5234202606010000004112", Extractors.reference("NEFT 5234202606010000004112 credited"))
    }

    @Test
    fun shortWordsAreNotReferences() {
        assertNull(Extractors.reference("Rs 340.00 debited from A/c XX9430"))
    }

    @Test
    fun availableBalanceIsSeparateFromGenericBalance() {
        val body = "A/c Bal is INR 7,258.51 CR and AVL Bal is INR 7,258.51"
        assertEquals(725851L, Extractors.availableBalance(body))
        assertEquals(725851L, Extractors.balance(body))
    }

    @Test
    fun avlBalanceIsNeverReadAsTheGenericBalance() {
        val body = "AVL Bal is INR 4,962.12 only"
        assertEquals(496212L, Extractors.availableBalance(body))
        assertNull(Extractors.balance(body))
    }

    @Test
    fun colonBalancesAreRead() {
        assertEquals(496212L, Extractors.balance("Balance: INR 4,962.12"))
        assertEquals(127000L, Extractors.balance("A/c Bal: 1270.00"))
    }

    @Test
    fun debitCounterpartyFromUpiTemplate() {
        val body = "A/c X8869 debited by Rs. 474.06 for UPI payment to Amit Verma K on 30-May-26. RRN: 404114426942"
        assertEquals("Amit Verma K", Extractors.counterparty(body, ParsedDirection.DEBIT))
    }

    @Test
    fun debitCounterpartyTowardsAutoPay() {
        val body = "Your account has been successfully debited with Rs 6.41 on 29-Jul-25 towards Google for GOOGLE AutoPay, RRN 111070434192"
        assertEquals("Google for GOOGLE AutoPay", Extractors.counterparty(body, ParsedDirection.DEBIT))
    }

    @Test
    fun creditCounterpartyFromFromClause() {
        val body = "Dear Customer, Acct XX658 is credited with Rs 2728.00 on 02-Oct-26 from SURYAA VISHAL J. UPI:601125936107-ICICI Bank"
        assertEquals("SURYAA VISHAL J.", Extractors.counterparty(body, ParsedDirection.CREDIT))
    }

    @Test
    fun creditCounterpartyByClause() {
        val body = "Your a/c xxxx 7535 has been credited by INR 617.50 on 01-OCT-2026 by APB Credit Through PFMS  A09720063E406."
        assertEquals("APB Credit Through PFMS", Extractors.counterparty(body, ParsedDirection.CREDIT))
    }

    @Test
    fun balanceFragmentIsNeverACounterparty() {
        val body = "A/c X8869 credited with Rs. 4303.61 on 30-Sep-26 from FATIMA SHAIKH RRN: 359435189991"
        assertEquals("FATIMA SHAIKH", Extractors.counterparty(body, ParsedDirection.CREDIT))
    }

    @Test
    fun noCounterpartyGivesNull() {
        val body = "Rs 340.00 debited from A/c XX9430 on 03-Oct-26"
        assertNull(Extractors.counterparty(body, ParsedDirection.DEBIT))
    }

    @Test
    fun everyExtractorIsSafeOnEmptyInput() {
        assertNull(Extractors.amount(""))
        assertNull(Extractors.direction(""))
        assertNull(Extractors.accountDigits(""))
        assertNull(Extractors.reference(""))
        assertNull(Extractors.balance(""))
        assertNull(Extractors.availableBalance(""))
        assertNull(Extractors.counterparty("", ParsedDirection.DEBIT))
        assertEquals(fallback, Extractors.date("", fallback))
        assertNotNull(SenderNormalizer.normalize(""))
    }
}