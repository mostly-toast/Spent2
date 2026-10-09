package com.aditya.spent.sms

object TransactionDetector {

    fun isTransactionMessage(body: String): Boolean {
        if (Extractors.amount(body) == null) return false
        if (Extractors.direction(body) == null) return false
        return Extractors.accountDigits(body) != null || Extractors.reference(body) != null
    }

    /**
     * Money moved and the account is identifiable, but no amount can be read.
     * The amount is never guessed, so the message goes to the user instead of
     * being dropped or stored with an invented value.
     */
    fun looksFinancialButUnparsed(body: String): Boolean =
        Extractors.amount(body) == null &&
            Extractors.direction(body) != null &&
            (Extractors.accountDigits(body) != null || Extractors.reference(body) != null)

    /**
     * Anything that blocks money in an account is a money event the user needs to
     * see, so an unclassified hold or lien is queued rather than dropped. No
     * direction and no amount is guessed for it.
     */
    fun shouldQueueForReview(body: String): Boolean =
        looksFinancialButUnparsed(body) || mentionsBlockedAmount(body)

    fun mentionsBlockedAmount(body: String): Boolean = Extractors.mentionsBlockedAmount(body)
}