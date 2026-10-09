package com.aditya.spent.sms

import java.util.Locale

enum class SenderSuffix { TRANSACTIONAL, SERVICE, PROMOTIONAL, GOVERNMENT, UNKNOWN }

data class NormalizedSender(
    val raw: String,
    val header: String,
    val operatorPrefix: String?,
    val suffix: SenderSuffix,
) {
    val candidateHeaders: List<String> =
        listOfNotNull(header.takeIf { it.isNotBlank() }, raw.trim().uppercase(Locale.ROOT)).distinct()
}

object SenderNormalizer {
    private val withSuffix = Regex("^([A-Za-z]{2})-(.+)-([STPG])$")
    private val withPrefixOnly = Regex("^([A-Za-z]{2})-(.+)$")

    fun normalize(raw: String): NormalizedSender {
        val token = raw.trim().uppercase(Locale.ROOT)
        withSuffix.matchEntire(token)?.let { match ->
            return NormalizedSender(
                raw = token,
                header = match.groupValues[2],
                operatorPrefix = match.groupValues[1],
                suffix = suffixOf(match.groupValues[3]),
            )
        }
        withPrefixOnly.matchEntire(token)?.let { match ->
            return NormalizedSender(
                raw = token,
                header = match.groupValues[2],
                operatorPrefix = match.groupValues[1],
                suffix = SenderSuffix.UNKNOWN,
            )
        }
        return NormalizedSender(raw = token, header = token, operatorPrefix = null, suffix = SenderSuffix.UNKNOWN)
    }

    private fun suffixOf(letter: String): SenderSuffix = when (letter) {
        "S" -> SenderSuffix.SERVICE
        "T" -> SenderSuffix.TRANSACTIONAL
        "P" -> SenderSuffix.PROMOTIONAL
        "G" -> SenderSuffix.GOVERNMENT
        else -> SenderSuffix.UNKNOWN
    }
}