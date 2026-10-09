package com.aditya.spent.usecase

import com.aditya.spent.model.Account
import com.aditya.spent.model.ParsedTransaction
import com.aditya.spent.model.RawSms
import com.aditya.spent.model.ReviewItem
import com.aditya.spent.model.ReviewReason
import com.aditya.spent.model.Transaction
import com.aditya.spent.model.TransactionSource
import com.aditya.spent.port.Clock
import com.aditya.spent.port.TransactionExtractor
import com.aditya.spent.repository.AccountRepository
import com.aditya.spent.repository.ReviewQueueRepository
import com.aditya.spent.repository.SenderMappingRepository
import com.aditya.spent.repository.TransactionRepository
import com.aditya.spent.rules.Dedup
import com.aditya.spent.rules.TransferMatcher
import com.aditya.spent.sms.AccountResolver
import com.aditya.spent.sms.NormalizedSender
import com.aditya.spent.sms.SenderNormalizer
import com.aditya.spent.sms.SenderPreFilter
import com.aditya.spent.sms.SenderRegistry
import com.aditya.spent.sms.TransactionDetector
import kotlinx.coroutines.flow.first
import java.util.UUID

enum class DropReason { USER_IGNORED, NOT_A_HEADER, PROMOTIONAL_OR_GOVERNMENT, NOT_A_TRANSACTION }

sealed interface IngestResult {
    data class Success(val transaction: Transaction) : IngestResult
    object DuplicateSkipped : IngestResult
    object ReviewQueued : IngestResult
    data class Dropped(val reason: DropReason) : IngestResult
    data class SaveFailed(val cause: Throwable) : IngestResult
}

class IngestSms(
    private val extractor: TransactionExtractor,
    private val registry: SenderRegistry,
    private val clock: Clock,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val reviewQueue: ReviewQueueRepository,
    private val senderMappings: SenderMappingRepository,
) {
    operator suspend fun invoke(rawSms: RawSms): IngestResult {
        val sender = SenderNormalizer.normalize(rawSms.sender)
        when (val verdict = SenderPreFilter.evaluate(sender, senderMappings.isSenderIgnored(sender.header))) {
            is SenderPreFilter.Verdict.Dropped -> return IngestResult.Dropped(verdict.reason.toDropReason())
            SenderPreFilter.Verdict.Read -> Unit
        }
        if (!TransactionDetector.isTransactionMessage(rawSms.body)) {
            return if (TransactionDetector.shouldQueueForReview(rawSms.body)) {
                review(rawSms, ReviewReason.UNPARSEABLE, null)
            } else {
                IngestResult.Dropped(DropReason.NOT_A_TRANSACTION)
            }
        }

        val parsed = extractor.extract(rawSms) ?: return review(rawSms, ReviewReason.UNPARSEABLE, null)

        val accounts = accountRepository.observeAccounts().first()
        val account = when (val outcome = resolveAccount(sender, parsed, accounts)) {
            is AccountOutcome.Found -> outcome.account
            is AccountOutcome.Rejected -> return review(rawSms, outcome.reason, parsed)
        }

        val leg = buildLeg(rawSms, sender, parsed, account)

        when (val dedup = Dedup.evaluate(parsed, account.id, sender.header, leg.requireSmsHash(), transactionRepository)) {
            is Dedup.Verdict.Duplicate -> return IngestResult.DuplicateSkipped
            is Dedup.Verdict.PossibleDuplicate -> {
                transactionRepository.markNeedsReview(dedup.existing.id)
                return save(leg.copy(needsReview = true))
            }
            Dedup.Verdict.Unique -> Unit
        }

        val accountIds = accounts.map { it.id }.toSet()
        return when (val transfer = TransferMatcher.evaluate(leg, accountIds, transactionRepository)) {
            is TransferMatcher.Verdict.Matched -> transactionRepository
                .saveTransfer(leg, transfer.existingLeg, transfer.transfer)
                .fold(
                    onSuccess = { IngestResult.Success(leg.copy(transferId = transfer.transfer.id)) },
                    onFailure = { IngestResult.SaveFailed(it) },
                )
            is TransferMatcher.Verdict.Ambiguous -> save(leg.copy(needsReview = true)).also {
                if (it is IngestResult.Success) review(rawSms, ReviewReason.AMBIGUOUS_TRANSFER, parsed)
            }
            TransferMatcher.Verdict.None -> save(leg)
        }
    }

    private sealed interface AccountOutcome {
        data class Found(val account: Account) : AccountOutcome
        data class Rejected(val reason: ReviewReason) : AccountOutcome
    }

    private suspend fun resolveAccount(
        sender: NormalizedSender,
        parsed: ParsedTransaction,
        accounts: List<Account>,
    ): AccountOutcome {
        val bankName = registry.bankFor(sender.candidateHeaders)
        when (val byDigits = AccountResolver.byDigits(parsed.accountDigits, accounts, bankName)) {
            is AccountResolver.Resolution.Resolved -> return AccountOutcome.Found(byDigits.account)
            is AccountResolver.Resolution.Ambiguous -> return AccountOutcome.Rejected(ReviewReason.AMBIGUOUS_ACCOUNT)
            AccountResolver.Resolution.None -> Unit
        }

        val mappedId = senderMappings.accountIdForSender(sender.header)
        if (mappedId != null) {
            accounts.firstOrNull { it.id == mappedId }?.let { return AccountOutcome.Found(it) }
        }

        if (bankName != null) {
            when (val byHint = AccountResolver.byBankHint(accounts, bankName)) {
                is AccountResolver.Resolution.Resolved -> return AccountOutcome.Found(byHint.account)
                is AccountResolver.Resolution.Ambiguous -> Unit
                AccountResolver.Resolution.None -> Unit
            }
        }

        val reason = if (registry.isKnown(sender.candidateHeaders)) ReviewReason.NO_ACCOUNT_MATCH else ReviewReason.UNKNOWN_SENDER
        return AccountOutcome.Rejected(reason)
    }

    private fun buildLeg(
        rawSms: RawSms,
        sender: NormalizedSender,
        parsed: ParsedTransaction,
        account: Account,
    ): Transaction = Transaction(
        id = UUID.randomUUID().toString(),
        type = Dedup.transactionTypeOf(parsed.direction),
        amountPaise = parsed.amountPaise,
        accountId = account.id,
        categoryId = null,
        timestampMillis = parsed.timestamp.toEpochMilli(),
        counterparty = parsed.merchant,
        referenceNumber = parsed.referenceNumber,
        source = TransactionSource.SMS,
        originalSmsId = rawSms.id,
        balanceAfterPaise = parsed.balancePaise,
        senderHeader = sender.header,
        smsHash = Dedup.computeSmsHash(sender.raw, rawSms.body),
    )

    private suspend fun save(leg: Transaction): IngestResult =
        transactionRepository.add(leg).fold(
            onSuccess = { IngestResult.Success(leg) },
            onFailure = { IngestResult.SaveFailed(it) },
        )

    private suspend fun review(
        rawSms: RawSms,
        reason: ReviewReason,
        candidate: ParsedTransaction?,
    ): IngestResult {
        reviewQueue.add(
            ReviewItem(
                id = UUID.randomUUID().toString(),
                rawSms = rawSms,
                reason = reason,
                candidate = candidate,
                createdAt = clock.now(),
            )
        )
        return IngestResult.ReviewQueued
    }

    private fun SenderPreFilter.DropReason.toDropReason(): DropReason = when (this) {
        SenderPreFilter.DropReason.USER_IGNORED -> DropReason.USER_IGNORED
        SenderPreFilter.DropReason.NOT_A_HEADER -> DropReason.NOT_A_HEADER
        SenderPreFilter.DropReason.PROMOTIONAL_OR_GOVERNMENT -> DropReason.PROMOTIONAL_OR_GOVERNMENT
    }

    private fun Transaction.requireSmsHash(): String = requireNotNull(smsHash) { "SMS leg without a hash" }
}