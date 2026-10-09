package com.aditya.spent.repository

import com.aditya.spent.model.Transaction
import com.aditya.spent.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeTransactionRepository : TransactionRepository {
    private val _visible = MutableStateFlow<List<Transaction>>(emptyList())
    private val stored = linkedMapOf<String, Transaction>()

    override fun observeTransactions(): Flow<List<Transaction>> = _visible.asStateFlow()

    override suspend fun getTransaction(id: String): Transaction? = stored[id]

    override suspend fun findBySmsHash(smsHash: String): Transaction? =
        stored.values.firstOrNull { it.smsHash == smsHash }

    override suspend fun findByReference(accountId: String, referenceNumber: String): Transaction? =
        stored.values.firstOrNull { it.accountId == accountId && it.referenceNumber == referenceNumber }

    override suspend fun findByAccountAmountAndWindow(
        accountId: String,
        type: TransactionType,
        amountPaise: Long,
        fromMillis: Long,
        toMillis: Long,
    ): List<Transaction> = stored.values.filter {
        it.accountId == accountId && it.type == type && it.amountPaise == amountPaise &&
            it.timestampMillis in fromMillis..toMillis
    }

    override suspend fun add(transaction: Transaction): Result<Unit> = runCatching {
        require(!stored.containsKey(transaction.id)) { "Duplicate id ${transaction.id}" }
        stored[transaction.id] = transaction
        publish()
    }

    override suspend fun update(transaction: Transaction): Result<Unit> = runCatching {
        require(stored.containsKey(transaction.id)) { "Transaction not found" }
        stored[transaction.id] = transaction
        publish()
    }

    override suspend fun delete(id: String): Result<Unit> = runCatching {
        stored.remove(id)
        publish()
    }

    override suspend fun markNeedsReview(id: String): Result<Unit> = runCatching {
        val existing = requireNotNull(stored[id]) { "Transaction not found" }
        stored[id] = existing.copy(needsReview = true)
        publish()
    }

    override suspend fun saveTransfer(newLeg: Transaction, existingLeg: Transaction?, transfer: Transaction): Result<Unit> =
        runCatching {
            require(transfer.type == TransactionType.TRANSFER) { "Not a transfer" }
            require(!stored.containsKey(newLeg.id)) { "Leg already stored" }
            if (existingLeg != null) {
                require(stored.containsKey(existingLeg.id)) { "Existing leg not found" }
                stored[existingLeg.id] = stored.getValue(existingLeg.id).copy(transferId = transfer.id)
            }
            stored[newLeg.id] = newLeg.copy(transferId = transfer.id)
            stored[transfer.id] = transfer
            publish()
        }

    fun setTransactions(transactions: List<Transaction>) {
        stored.clear()
        transactions.forEach { stored[it.id] = it }
        publish()
    }

    fun storedTransactions(): List<Transaction> = stored.values.toList()

    private fun publish() {
        _visible.value = stored.values.filter { it.transferId == null }
    }
}