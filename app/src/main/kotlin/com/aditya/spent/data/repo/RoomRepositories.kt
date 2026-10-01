package com.aditya.spent.data.repo

import androidx.room.withTransaction
import com.aditya.spent.data.db.AccountDao
import com.aditya.spent.data.db.CategoryDao
import com.aditya.spent.data.db.SpentDatabase
import com.aditya.spent.data.db.TransactionDao
import com.aditya.spent.data.db.toEntity
import com.aditya.spent.data.db.toModel
import com.aditya.spent.model.Account
import com.aditya.spent.model.Category
import com.aditya.spent.model.Transaction
import com.aditya.spent.model.TransactionType
import com.aditya.spent.repository.AccountRepository
import com.aditya.spent.repository.CategoryRepository
import com.aditya.spent.repository.TransactionRepository
import com.aditya.spent.usecase.BalanceAdjustment
import com.aditya.spent.usecase.TransactionBalanceAdjustments
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomAccountRepository(
    private val database: SpentDatabase,
    private val accounts: AccountDao,
    private val transactions: TransactionDao,
) : AccountRepository {
    override fun observeAccounts(): Flow<List<Account>> = accounts.observeAll().map { it.map { entity -> entity.toModel() } }
    override suspend fun getAccount(id: String): Account? = accounts.get(id)?.toModel()
    override suspend fun add(account: Account): Result<Unit> = runCatching {
        validate(account)
        accounts.insert(account.copy(currentBalancePaise = account.openingBalancePaise).toEntity())
    }
    override suspend fun update(account: Account): Result<Unit> = runCatching {
        validate(account)
        val existing = requireNotNull(accounts.get(account.id)) { "Account not found" }
        val openingBalanceChange = account.openingBalancePaise - existing.openingBalancePaise
        accounts.update(account.copy(currentBalancePaise = existing.currentBalancePaise + openingBalanceChange).toEntity())
    }
    override suspend fun delete(id: String): Result<Unit> = runCatching {
        database.withTransaction {
            require(transactions.countReferencingAccount(id) == 0) { "Cannot delete an account with transactions" }
            accounts.delete(id)
        }
    }
    private fun validate(account: Account) {
        require(account.name.isNotBlank()) { "Account name is required" }
        require(account.bank.isNotBlank()) { "Bank is required" }
        require(account.type.isNotBlank()) { "Account type is required" }
        require(account.lastFourDigits.matches(Regex("\\d{4}"))) { "Last four digits must be exactly four digits" }
    }
}

class RoomCategoryRepository(private val categories: CategoryDao) : CategoryRepository {
    override fun observeCategories(type: TransactionType): Flow<List<Category>> = categories.observeByType(type.name).map { it.map { entity -> entity.toModel() } }
}

class RoomTransactionRepository(
    private val database: SpentDatabase,
    private val accounts: AccountDao,
    private val categories: CategoryDao,
    private val transactions: TransactionDao,
    private val balanceAdjustments: TransactionBalanceAdjustments,
) : TransactionRepository {
    override fun observeTransactions(): Flow<List<Transaction>> = transactions.observeAll().map { it.map { entity -> entity.toModel() } }
    override suspend fun getTransaction(id: String): Transaction? = transactions.get(id)?.toModel()
    override suspend fun add(transaction: Transaction): Result<Unit> = runCatching {
        database.withTransaction {
            validateCategory(transaction)
            apply(balanceAdjustments(transaction))
            transactions.insert(transaction.toEntity())
        }
    }
    override suspend fun update(transaction: Transaction): Result<Unit> = runCatching {
        database.withTransaction {
            val previous = requireNotNull(transactions.get(transaction.id)) { "Transaction not found" }.toModel()
            validateCategory(transaction)
            apply(balanceAdjustments(previous).map { it.copy(deltaPaise = -it.deltaPaise) })
            apply(balanceAdjustments(transaction))
            transactions.update(transaction.toEntity())
        }
    }
    override suspend fun delete(id: String): Result<Unit> = runCatching {
        database.withTransaction {
            val previous = requireNotNull(transactions.get(id)) { "Transaction not found" }.toModel()
            apply(balanceAdjustments(previous).map { it.copy(deltaPaise = -it.deltaPaise) })
            transactions.delete(id)
        }
    }
    private suspend fun apply(adjustments: List<BalanceAdjustment>) {
        adjustments.forEach { adjustment ->
            require(accounts.adjustBalance(adjustment.accountId, adjustment.deltaPaise) == 1) { "Account not found" }
        }
    }
    private suspend fun validateCategory(transaction: Transaction) {
        transaction.categoryId?.let { categoryId ->
            val category = requireNotNull(categories.get(categoryId)) { "Category not found" }
            require(!category.isArchived && category.type == transaction.type.name) { "Category does not match transaction type" }
        }
    }
}
