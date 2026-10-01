package com.aditya.spent.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aditya.spent.model.Account
import com.aditya.spent.repository.AccountRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountsUiState(val accounts: List<Account> = emptyList())

class AccountsViewModel(private val repository: AccountRepository) : ViewModel() {
    val uiState: StateFlow<AccountsUiState> = repository.observeAccounts()
        .map(::AccountsUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    fun save(account: Account) = viewModelScope.launch {
        if (repository.getAccount(account.id) == null) repository.add(account) else repository.update(account)
    }
    fun delete(id: String) = viewModelScope.launch { repository.delete(id) }
}
