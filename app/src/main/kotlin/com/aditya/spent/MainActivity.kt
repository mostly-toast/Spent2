package com.aditya.spent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aditya.spent.di.AppContainer
import com.aditya.spent.ui.SpentApp
import com.aditya.spent.ui.accounts.AccountsViewModel
import com.aditya.spent.ui.transactions.TransactionsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = AppContainer(applicationContext)
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
                AccountsViewModel::class.java -> AccountsViewModel(container.accountRepository) as T
                TransactionsViewModel::class.java -> TransactionsViewModel(container.accountRepository, container.categoryRepository, container.transactionRepository) as T
                else -> error("Unknown ViewModel: ${modelClass.name}")
            }
        }
        val accountsViewModel = ViewModelProvider(this, factory)[AccountsViewModel::class.java]
        val transactionsViewModel = ViewModelProvider(this, factory)[TransactionsViewModel::class.java]
        enableEdgeToEdge()
        setContent { SpentApp(accountsViewModel, transactionsViewModel) }
    }
}
