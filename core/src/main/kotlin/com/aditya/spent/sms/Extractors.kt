package com.aditya.spent.sms

import com.aditya.spent.model.Money
import com.aditya.spent.model.ParsedDirection
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import java.util.regex.Pattern

object Extractors {

    private fun regex(pattern: String): Pattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)

    private val amountPattern = regex("\\b(?:Rs\\.?|INR)\\s*([\\d,]+(?:\\.\\d{1,2})?)(?!\\d)(?!\\.\\d)")

    private val accountWithKeywordPattern = regex(
        "(?:Acct|Account|A/c|A/C)\\s*(?:No)?\\s*[.:]?\\s*[Xx]*\\s*(\\d{3,4})(?!\\d)"
    )
    private val accountMaskedPattern = regex("[Xx]{2,}\\s*(\\d{3,4})(?!\\d)")

    private val dateAfterOnPattern = regex("on\\s+(\\d{1,2})[-.]([A-Za-z]{3})[-.](\\d{2,4})")
    private val dateMonthYearPattern = regex("(\\d{1,2})[-.]([A-Za-z]{3})[-.](\\d{4})")
    private val dateMonthShortYearPattern = regex("(\\d{1,2})[-.]([A-Za-z]{3})[-.](\\d{2})(?!\\d)")
    private val dateNumericPattern = regex("(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{2,4})")

    private val referencePattern = regex(
        "(?:UPI|RRN|Ref|Reference)[:\\s]*([A-Za-z0-9]{9,})" +
            "|UPI_\\d{8}_\\d+_RET_([A-Za-z0-9]{6,})" +
            "|\\b(?:NEFT|IMPS|RTGS)\\s+([A-Z0-9]{12,})\\b" +
            "|\\b(?=[A-Za-z0-9]*\\d)([A-Za-z0-9]{10,})\\b"
    )
    private val senderTagPattern = regex("-[A-Za-z ]+(?:Bank|Maharashtra)[A-Za-z ]*$")

    private val balancePattern = regex("(?<!AVL\\s)(?:A/c\\s*Bal|Ac\\s*Bal|Bal(?:ance)?)\\s*(?:is|:)?\\s*(?:INR|Rs\\.?)?\\s*([\\d,]+(?:\\.\\d{1,2})?)(?!\\d)(?!\\.\\d)")
    private val availableBalancePattern = regex("(?:AVL\\s*Bal|Avail\\s*Bal|Available\\s*Bal)\\s*(?:is|:)?\\s*(?:INR|Rs\\.?)?\\s*([\\d,]+(?:\\.\\d{1,2})?)(?!\\d)(?!\\.\\d)")

    private val debitWords = listOf("debited", "debit", "paid to", "paid", "spent", "withdrawn", "withdrawal")
    private val creditWords = listOf("credited", "credit", "received", "refunded")
    private val negationPattern = regex("(?:not|never|n't|cannot)\\s+$")

    private val topUpPattern = regex("\\btop[-\\s]?ups?\\b|\\btopped\\s+up\\b")
    private val lienOrHoldPattern = regex("\\b(?:lien|hold|margin)\\b")

    private val holdDebitPattern = regex(
        "(?s)\\b(?:lien|hold|margin)\\b.{0,80}?\\b(?:applied|placed|kept|blocked|reserved)\\b",
    )

    private val debitCounterpartyPatterns = listOf(
        regex("(?:successfully\\s+)?debited\\s+with\\s+(?:Rs\\.?|INR)\\s*[\\d,.]+(?:\\.\\d{1,2})?\\s+on\\s+\\d{1,2}-[A-Za-z]{3}-\\d{2,4}\\s+towards\\s+([^.;]+?)(?:\\.|,|;|\\s+RRN|\\s+UPI|$)"),
        regex("(?:debited (?:for|by)|paid to|payment to)\\s+(?:Rs\\.?|INR)\\s*[\\d,.]+(?:\\.\\d{1,2})?\\s+(?:for\\s+UPI\\s+payment\\s+to|paid\\s+to|payment\\s+to)\\s+([^.;]+?)(?:\\.|;|\\s+UPI|\\s+RRN|\\s+on|\\s+Call|\\s+SMS|$)"),
        regex("(?:debited (?:for|by)|paid to|payment to)\\s+(?:Rs\\.?|INR)\\s*[\\d,.]+(?:\\.\\d{1,2})?\\s+on\\s+\\d{1,2}-[A-Za-z]{3}-\\d{2,4}\\s*[.;]\\s*([^.;]+?)(?:\\.|;|\\s+UPI|\\s+RRN|\\s+credited|\\s+Call|\\s+SMS|$)"),
        regex("(?:debited (?:for|by)|paid to|payment to)\\s+([^.;]+?)(?:\\.|;|\\s+UPI|\\s+RRN|\\s+on|\\s+Call|\\s+SMS|$)"),
    )
    private val creditAfterUtrPattern = regex("\\b(?:NEFT|IMPS|RTGS)\\s+[A-Z0-9]{12,}\\s+(.+?)(?=\\s*(?:A/c\\s*Bal|AVL\\s*Bal|Avl\\s*Bal|\\.|;|$))")
    private val creditCounterpartyPatterns = listOf(
        regex("(?:from|credited by)\\s+(?:INR|Rs\\.?)\\s*[\\d,.]+(?:\\.\\d{1,2})?\\s+on\\s+\\d{1,2}-[A-Za-z]{3}-\\d{2,4}\\s+by\\s+(.*?)(?=\\s+(?:UPI|RRN|Call|SMS|\\d{1,2}-[A-Za-z]{3}-\\d{2,4}|[A-Z0-9]{10,})|\\.\\s|;\\s|$)"),
        regex("(?:from|credited by)\\s+(.*?\\.?)(?=\\s+(?:UPI|RRN|Call|SMS|\\d{1,2}-[A-Za-z]{3}-\\d{2,4}|[A-Z0-9]{10,})|\\.\\s|;\\s|$)"),
    )
    private val balanceFragmentPattern = regex("^(?:AVL|Avl|A/c|Ac|Ava?ilable|Total)?\\s*Bal(?:ance)?\\b")

    private val monthNumbers = mapOf(
        "Jan" to 1, "Feb" to 2, "Mar" to 3, "Apr" to 4,
        "May" to 5, "Jun" to 6, "Jul" to 7, "Aug" to 8,
        "Sep" to 9, "Oct" to 10, "Nov" to 11, "Dec" to 12,
    )

    fun mentionsBlockedAmount(body: String): Boolean = lienOrHoldPattern.matcher(body).find()

    fun amount(body: String): Long? = firstCapture(amountPattern, body)?.let { paise(it) }

    fun direction(body: String): ParsedDirection? {
        val lower = body.lowercase(Locale.ROOT)
        return when {
            hasUnegatedWord(lower, debitWords) -> ParsedDirection.DEBIT
            hasUnegatedWord(lower, creditWords) -> ParsedDirection.CREDIT
            topUpPattern.matcher(body).find() -> ParsedDirection.DEBIT
            holdDebitPattern.matcher(body).find() -> ParsedDirection.DEBIT
            else -> null
        }
    }

    fun accountDigits(body: String): String? =
        firstCapture(accountWithKeywordPattern, body) ?: firstCapture(accountMaskedPattern, body)

    fun date(body: String, fallback: Instant): Instant {
        dateAfterOnPattern.matcher(body).let { matcher ->
            if (matcher.find()) return fromMonthName(matcher, fallback)
        }
        dateMonthYearPattern.matcher(body).let { matcher ->
            if (matcher.find()) return fromMonthName(matcher, fallback)
        }
        dateMonthShortYearPattern.matcher(body).let { matcher ->
            if (matcher.find()) return fromMonthName(matcher, fallback)
        }
        dateNumericPattern.matcher(body).let { matcher ->
            if (matcher.find()) {
                val day = matcher.group(1)?.toIntOrNull()
                val month = matcher.group(2)?.toIntOrNull()
                val rawYear = matcher.group(3)
                val year = when {
                    rawYear == null -> null
                    rawYear.length == 2 -> rawYear.toIntOrNull()?.let { 2000 + it }
                    else -> rawYear.toIntOrNull()
                }
                if (day != null && month != null && year != null) {
                    atStartOfDay(year, month, day)?.let { return it }
                }
            }
        }
        return fallback
    }

    fun reference(body: String): String? {
        val matcher = referencePattern.matcher(body)
        if (!matcher.find()) return null
        val captured = (1..4).firstNotNullOfOrNull { matcher.group(it) } ?: return null
        val cleaned = senderTagPattern.matcher(captured).replaceAll("").trim()
        return cleaned.ifBlank { null }
    }

    fun balance(body: String): Long? = firstCapture(balancePattern, body)?.let { paise(it) }

    fun availableBalance(body: String): Long? = firstCapture(availableBalancePattern, body)?.let { paise(it) }

    fun counterparty(body: String, direction: ParsedDirection): String? {
        val patterns = when (direction) {
            ParsedDirection.DEBIT -> debitCounterpartyPatterns
            ParsedDirection.CREDIT -> listOf(creditAfterUtrPattern) + creditCounterpartyPatterns
        }
        for (pattern in patterns) {
            val matcher = pattern.matcher(body)
            if (!matcher.find()) continue
            val candidate = (1..matcher.groupCount()).firstNotNullOfOrNull { matcher.group(it) }?.trim()
            if (!candidate.isNullOrBlank() && !balanceFragmentPattern.matcher(candidate).find()) return candidate
        }
        return null
    }

    private fun firstCapture(pattern: Pattern, body: String): String? {
        val matcher = pattern.matcher(body)
        if (!matcher.find()) return null
        return matcher.group(1)
    }

    private fun paise(raw: String): Long? = Money.rupeesToPaise(raw.replace(",", "")).getOrNull()

    private fun hasUnegatedWord(lowerBody: String, words: List<String>): Boolean =
        words.any { word -> occurrences(lowerBody, word).any { index -> !isNegated(lowerBody, index) } }

    private fun occurrences(lowerBody: String, word: String): List<Int> {
        val found = mutableListOf<Int>()
        var index = lowerBody.indexOf(word)
        while (index >= 0) {
            if (isWordOccurrence(lowerBody, word, index)) found += index
            index = lowerBody.indexOf(word, index + word.length)
        }
        return found
    }

    private fun isWordOccurrence(lowerBody: String, word: String, index: Int): Boolean {
        val before = lowerBody.getOrNull(index - 1)
        if (before != null && (before.isLetterOrDigit() || before == '_')) return false
        val after = lowerBody.getOrNull(index + word.length)
        return after == null || !(after.isLetterOrDigit() || after == '_')
    }

    private fun isNegated(lowerBody: String, index: Int): Boolean {
        if (index == 0) return false
        val before = lowerBody.substring(maxOf(0, index - 12), index)
        return negationPattern.matcher(before).find()
    }

    private fun fromMonthName(matcher: java.util.regex.Matcher, fallback: Instant): Instant {
        val day = matcher.group(1).toIntOrNull() ?: return fallback
        val monthName = matcher.group(2)?.lowercase(Locale.ROOT)?.replaceFirstChar { it.uppercase() }
        val month = monthNumbers[monthName] ?: return fallback
        val rawYear = matcher.group(3) ?: return fallback
        val year = if (rawYear.length == 2) 2000 + (rawYear.toIntOrNull() ?: return fallback) else rawYear.toIntOrNull() ?: return fallback
        return atStartOfDay(year, month, day) ?: fallback
    }

    private fun atStartOfDay(year: Int, month: Int, day: Int): Instant? = runCatching {
        LocalDateTime.of(year, month, day, 0, 0).toInstant(ZoneOffset.UTC)
    }.getOrNull()
}