package com.aditya.spent.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aditya.spent.model.Account
import com.aditya.spent.model.Category
import com.aditya.spent.model.Transaction
import com.aditya.spent.model.TransactionType
import com.aditya.spent.repository.AccountRepository
import com.aditya.spent.repository.CategoryRepository
import com.aditya.spent.repository.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TransactionsUiState(
    val accounts: List<Account> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val incomeCategories: List<Category> = emptyList(),
    val expenseCategories: List<Category> = emptyList(),
)

class TransactionsViewModel(
    accounts: AccountRepository,
    categories: CategoryRepository,
    private val transactions: TransactionRepository,
) : ViewModel() {
    val uiState: StateFlow<TransactionsUiState> = combine(
        accounts.observeAccounts(),
        transactions.observeTransactions(),
        categories.observeCategories(TransactionType.INCOME),
        categories.observeCategories(TransactionType.EXPENSE),
    ) { accountList, transactionList, income, expense ->
        TransactionsUiState(accountList, transactionList, income, expense)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    fun save(transaction: Transaction) = viewModelScope.launch {
        if (transactions.getTransaction(transaction.id) == null) transactions.add(transaction) else transactions.update(transaction)
    }
    fun delete(id: String) = viewModelScope.launch { transactions.delete(id) }
}
