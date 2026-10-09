package com.aditya.spent.sms

import com.aditya.spent.model.Account

object AccountResolver {

    sealed interface Resolution {
        data class Resolved(val account: Account, val via: Source) : Resolution
        data class Ambiguous(val candidates: List<Account>) : Resolution
        object None : Resolution
    }

    enum class Source { ACCOUNT_DIGITS, BANK_HINT, MAPPED_SENDER }

    private val bankNoise = setOf("bank", "limited", "ltd", "the", "of", "and", "plc", "co", "branch")

    fun byDigits(accountDigits: String?, accounts: List<Account>, bankName: String?): Resolution {
        val digits = accountDigits?.takeIf { it.length >= 3 } ?: return Resolution.None
        val candidates = accounts.filter { account ->
            val lastFour = account.lastFourDigits
            lastFour.length >= digits.length && lastFour.endsWith(digits)
        }
        return when {
            candidates.isEmpty() -> Resolution.None
            candidates.size == 1 -> Resolution.Resolved(candidates.first(), Source.ACCOUNT_DIGITS)
            else -> byBankHint(candidates, bankName)
        }
    }

    fun byBankHint(accounts: List<Account>, bankName: String?): Resolution {
        val bank = bankName?.takeIf { it.isNotBlank() } ?: return Resolution.None
        val wanted = tokens(bank)
        if (wanted.isEmpty()) return Resolution.None
        val matching = accounts.filter { account ->
            val accountTokens = tokens(account.bank) + tokens(account.name)
            wanted.any { it in accountTokens }
        }
        return when {
            matching.size == 1 -> Resolution.Resolved(matching.first(), Source.BANK_HINT)
            matching.isEmpty() -> Resolution.None
            else -> Resolution.Ambiguous(matching)
        }
    }

    private fun tokens(value: String): Set<String> =
        value.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() && it !in bankNoise }.toSet()
}