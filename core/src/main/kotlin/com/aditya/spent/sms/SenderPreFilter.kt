package com.aditya.spent.sms

object SenderPreFilter {
    enum class DropReason { USER_IGNORED, NOT_A_HEADER, PROMOTIONAL_OR_GOVERNMENT }

    sealed interface Verdict {
        object Read : Verdict
        data class Dropped(val reason: DropReason) : Verdict
    }

    private val alphanumericHeader = Regex("^[A-Za-z0-9]+(-[A-Za-z0-9]+)*$")

    fun isAlphanumericHeader(sender: String): Boolean {
        val token = sender.trim()
        return token.matches(alphanumericHeader) && token.any { it.isLetter() }
    }

    fun evaluate(normalized: NormalizedSender, userIgnored: Boolean): Verdict = when {
        userIgnored -> Verdict.Dropped(DropReason.USER_IGNORED)
        !isAlphanumericHeader(normalized.raw) -> Verdict.Dropped(DropReason.NOT_A_HEADER)
        normalized.suffix == SenderSuffix.PROMOTIONAL || normalized.suffix == SenderSuffix.GOVERNMENT ->
            Verdict.Dropped(DropReason.PROMOTIONAL_OR_GOVERNMENT)
        else -> Verdict.Read
    }
}