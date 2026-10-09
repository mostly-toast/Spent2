package com.aditya.spent.repository

import com.aditya.spent.model.Account
import com.aditya.spent.model.Category
import com.aditya.spent.model.Transaction
import com.aditya.spent.model.TransactionType
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun observeAccounts(): Flow<List<Account>>
    suspend fun getAccount(id: String): Account?
    suspend fun add(account: Account): Result<Unit>
    suspend fun update(account: Account): Result<Unit>
    suspend fun delete(id: String): Result<Unit>
}

interface CategoryRepository {
    fun observeCategories(type: TransactionType): Flow<List<Category>>
}

interface TransactionRepository {
    fun observeTransactions(): Flow<List<Transaction>>
    suspend fun getTransaction(id: String): Transaction?
    suspend fun findBySmsHash(smsHash: String): Transaction?
    suspend fun findByReference(accountId: String, referenceNumber: String): Transaction?
    suspend fun findByAccountAmountAndWindow(
        accountId: String,
        type: TransactionType,
        amountPaise: Long,
        fromMillis: Long,
        toMillis: Long,
    ): List<Transaction>
    suspend fun add(transaction: Transaction): Result<Unit>
    suspend fun update(transaction: Transaction): Result<Unit>
    suspend fun delete(id: String): Result<Unit>
    suspend fun markNeedsReview(id: String): Result<Unit>
    suspend fun saveTransfer(newLeg: Transaction, existingLeg: Transaction?, transfer: Transaction): Result<Unit>
}