package com.aditya.spent.sms

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransactionDetectorTest {

    private val detector = TransactionDetector

    @Test
    fun debitWithAccountIsATransaction() {
        assertTrue(detector.isTransactionMessage("ICICI Bank A/c XX943 debited Rs 340.00 on 03-Oct-26"))
    }

    @Test
    fun creditWithAccountIsATransaction() {
        assertTrue(detector.isTransactionMessage("Bank of Maharashtra A/c XX7095 credited Rs 3300.00 on 01-Oct-26"))
    }

    @Test
    fun transactionWithOnlyAReferenceIsATransaction() {
        assertTrue(detector.isTransactionMessage("Rs 200.00 debited for UPI payment to PAYTM*Nodal123456@paytm UPI: 40382640461"))
    }

    @Test
    fun walletTopUpIsATransaction() {
        assertTrue(
            detector.isTransactionMessage(
                "UPI LITE Top-up amounting to Rs. 474.06  has been successful. RRN : 636083778353 - Bank of Maharashtra",
            ),
        )
    }

    @Test
    fun asbaLienIsATransaction() {
        assertTrue(
            detector.isTransactionMessage(
                "ASBA lien of Rs. 31,668.48 has been applied in your account No:xxxx7435  at 30-09-2026 02:09:45 -Bank of Maharashtra",
            ),
        )
    }

    @Test
    fun amountWithoutDirectionIsNotATransaction() {
        assertFalse(detector.isTransactionMessage("Your A/c XX9430 statement for Rs 340.00 is ready on 03-Oct-26"))
    }

    @Test
    fun directionWithoutAmountIsNotATransaction() {
        assertFalse(detector.isTransactionMessage("A/c XX9430 was debited on 03-Oct-26 for fuel"))
    }

    @Test
    fun amountAndDirectionWithoutAccountOrReferenceIsNotATransaction() {
        assertFalse(detector.isTransactionMessage("Rs 340.00 debited for your order"))
    }

    @Test
    fun marketingMessageWithAPriceIsNotATransaction() {
        assertFalse(detector.isTransactionMessage("Sale! Flat 40% off on Rs 1299.00 this weekend. Order now."))
    }

    @Test
    fun plainTextIsNotATransaction() {
        assertFalse(detector.isTransactionMessage("This is not a transaction message"))
    }

    @Test
    fun transactionLimitNoticeIsNotATransaction() {
        assertFalse(
            detector.isTransactionMessage(
                "Per day cooling period limit for UPI transactions is Rs 3184 via WA for 72 hours after new user registration-ICICI Bank",
            ),
        )
        assertFalse(
            detector.isTransactionMessage(
                "You have successfully linked Account XX391 to KIRAN JOSHI on iMobile. As per NPCI guidelines, your total transaction limit is Rs3184 for 24 hours.-ICICI Bank",
            ),
        )
    }

    @Test
    fun unreadableAmountOnAKnownAccountNeedsTheUser() {
        assertFalse(detector.isTransactionMessage("This is not a transaction message"))
        assertTrue(
            detector.looksFinancialButUnparsed(
                "Dear customer, A hold of INR          49621.723 has been applied on your A/C XXXXXXX7435Due to Sandeep Gaikwad     11-UPI            For queries,contact your Home Branch-Bank of Maharashtra.",
            ),
        )
    }

    @Test
    fun unreadableAmountWithoutAnAccountOrReferenceIsJustDropped() {
        assertFalse(
            detector.looksFinancialButUnparsed(
                "Your total transaction limit is Rs3184 for 24 hours.-ICICI Bank",
            ),
        )
        assertFalse(detector.looksFinancialButUnparsed("This is not a transaction message"))
    }

    @Test
    fun anyHoldOrLienReachesTheUserEvenWhenUnclassified() {
        val appliedLien = "ASBA lien of Rs. 31,668.48 has been applied in your account No:xxxx7435  at 30-09-2026 02:09:45 -Bank of Maharashtra"
        assertTrue(detector.mentionsBlockedAmount(appliedLien))
        assertTrue(detector.shouldQueueForReview(appliedLien))
        assertTrue(
            detector.mentionsBlockedAmount(
                "Dear customer, A hold of INR          49621.723 has been applied on your A/C XXXXXXX7435Due to Sandeep Gaikwad     11-UPI            For queries,contact your Home Branch-Bank of Maharashtra.",
            ),
        )
    }

    @Test
    fun messagesThatDoNotBlockMoneyAreNotQueued() {
        assertFalse(detector.shouldQueueForReview("Dear Customer, your AutoPay mandate is successfully revoked towards Google for Rs 5218.59, RRN 434789637457-Bank of Maharashtra"))
        assertFalse(detector.shouldQueueForReview("Sale! Flat 40% off on Rs 1299.00 this weekend. Order now."))
        assertFalse(detector.shouldQueueForReview("This is not a transaction message"))
    }

    @Test
    fun readableMessagesAreNotUnparsed() {
        assertFalse(
            detector.looksFinancialButUnparsed(
                "A/c X8869 debited by Rs. 474.06 for UPI payment to Amit Verma K on 30-May-26. RRN: 404114426942",
            ),
        )
    }
}