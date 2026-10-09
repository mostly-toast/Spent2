package com.aditya.spent.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY name") fun observeAll(): Flow<List<AccountEntity>>
    @Query("SELECT * FROM accounts WHERE id = :id") suspend fun get(id: String): AccountEntity?
    @Insert suspend fun insert(account: AccountEntity)
    @Update suspend fun update(account: AccountEntity)
    @Query("DELETE FROM accounts WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE accounts SET currentBalancePaise = currentBalancePaise + :delta WHERE id = :id") suspend fun adjustBalance(id: String, delta: Long): Int
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE type = :type AND isArchived = 0 ORDER BY name") fun observeByType(type: String): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM categories WHERE id = :id") suspend fun get(id: String): CategoryEntity?
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE transferId IS NULL ORDER BY timestampMillis DESC") fun observeAll(): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM transactions WHERE id = :id") suspend fun get(id: String): TransactionEntity?
    @Query("SELECT * FROM transactions WHERE smsHash = :smsHash LIMIT 1") suspend fun findBySmsHash(smsHash: String): TransactionEntity?
    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND referenceNumber = :referenceNumber LIMIT 1") suspend fun findByReference(accountId: String, referenceNumber: String): TransactionEntity?
    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND type = :type AND amountPaise = :amountPaise AND timestampMillis BETWEEN :fromMillis AND :toMillis") suspend fun findByAccountAmountAndWindow(accountId: String, type: String, amountPaise: Long, fromMillis: Long, toMillis: Long): List<TransactionEntity>
    @Insert suspend fun insert(transaction: TransactionEntity)
    @Update suspend fun update(transaction: TransactionEntity)
    @Query("UPDATE transactions SET needsReview = 1 WHERE id = :id") suspend fun markNeedsReview(id: String)
    @Query("UPDATE transactions SET transferId = :transferId WHERE id IN (:ids)") suspend fun linkToTransfer(ids: List<String>, transferId: String)
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId OR destinationAccountId = :accountId") suspend fun countReferencingAccount(accountId: String): Int
}
