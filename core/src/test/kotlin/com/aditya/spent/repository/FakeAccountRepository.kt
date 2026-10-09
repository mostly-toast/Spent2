package com.aditya.spent.repository

import com.aditya.spent.model.Account
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAccountRepository : AccountRepository {
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    override fun observeAccounts(): Flow<List<Account>> = _accounts.asStateFlow()

    private val accountsMap = mutableMapOf<String, Account>()

    override suspend fun getAccount(id: String): Account? = accountsMap[id]

    override suspend fun add(account: Account): Result<Unit> = runCatching {
        accountsMap[account.id] = account
        _accounts.value = accountsMap.values.toList()
    }

    override suspend fun update(account: Account): Result<Unit> = runCatching {
        accountsMap[account.id] = account
        _accounts.value = accountsMap.values.toList()
    }

    override suspend fun delete(id: String): Result<Unit> = runCatching {
        accountsMap.remove(id)
        _accounts.value = accountsMap.values.toList()
    }

    fun setAccounts(accounts: List<Account>) {
        accountsMap.clear()
        accounts.forEach { accountsMap[it.id] = it }
        _accounts.value = accounts
    }
}