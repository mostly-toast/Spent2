package com.aditya.spent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aditya.spent.model.Account
import com.aditya.spent.model.Category
import com.aditya.spent.model.Money
import com.aditya.spent.model.Transaction
import com.aditya.spent.model.TransactionSource
import com.aditya.spent.model.TransactionType
import com.aditya.spent.ui.accounts.AccountsViewModel
import com.aditya.spent.ui.transactions.TransactionsUiState
import com.aditya.spent.ui.transactions.TransactionsViewModel
import java.util.UUID

@Composable
fun SpentApp(accountsViewModel: AccountsViewModel, transactionsViewModel: TransactionsViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Accounts") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Transactions") })
            }
            if (selectedTab == 0) AccountsScreen(accountsViewModel) else TransactionsScreen(transactionsViewModel)
        }
    }
}

@Composable
private fun AccountsScreen(viewModel: AccountsViewModel) {
    val state by viewModel.uiState.collectAsState()
    var editing by remember { mutableStateOf<Account?>(null) }
    var adding by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("Add account") }
        if (adding || editing != null) AccountEditor(editing, onSave = { viewModel.save(it); adding = false; editing = null }, onCancel = { adding = false; editing = null })
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.accounts, key = { it.id }) { account ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) { Text(account.name, style = MaterialTheme.typography.titleMedium); Text("${account.bank} ${account.type} • ${account.lastFourDigits}"); Text(Money.paiseToRupees(account.currentBalancePaise)) }
                        Column { TextButton(onClick = { editing = account }) { Text("Edit") }; TextButton(onClick = { viewModel.delete(account.id) }) { Text("Delete") } }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountEditor(account: Account?, onSave: (Account) -> Unit, onCancel: () -> Unit) {
    var name by remember(account) { mutableStateOf(account?.name.orEmpty()) }
    var bank by remember(account) { mutableStateOf(account?.bank.orEmpty()) }
    var type by remember(account) { mutableStateOf(account?.type.orEmpty()) }
    var lastFour by remember(account) { mutableStateOf(account?.lastFourDigits.orEmpty()) }
    var opening by remember(account) { mutableStateOf(account?.openingBalancePaise?.let { Money.paiseToRupees(it).removePrefix("INR ").trim() }.orEmpty()) }
    Card(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
            Text(if (account == null) "New account" else "Edit account")
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(bank, { bank = it }, label = { Text("Bank") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(type, { type = it }, label = { Text("Type") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(lastFour, { lastFour = it.filter(Char::isDigit).take(4) }, label = { Text("Last four digits") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(opening, { opening = it }, label = { Text("Opening balance (₹)") }, modifier = Modifier.fillMaxWidth())
            Row { Button(onClick = {
                Money.rupeesToPaise(opening).fold(
                    onFailure = { /* validation error - button disabled */ },
                    onSuccess = { openingPaise ->
                        onSave(Account(
                            account?.id ?: UUID.randomUUID().toString(),
                            name, bank, type, lastFour,
                            openingPaise,
                            account?.currentBalancePaise ?: openingPaise
                        ))
                    }
                )
            }, enabled = name.isNotBlank() && bank.isNotBlank() && type.isNotBlank() && lastFour.length == 4 && Money.rupeesToPaise(opening).isSuccess) { Text("Save") }; TextButton(onClick = onCancel) { Text("Cancel") } }
        }
    }
}

@Composable
private fun TransactionsScreen(viewModel: TransactionsViewModel) {
    val state by viewModel.uiState.collectAsState()
    var editing by remember { mutableStateOf<Transaction?>(null) }
    var adding by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = { adding = true }, enabled = state.accounts.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Add transaction") }
        if (adding || editing != null) TransactionEditor(editing, state, onSave = { viewModel.save(it); adding = false; editing = null }, onCancel = { adding = false; editing = null })
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.transactions, key = { it.id }) { transaction ->
                Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text(transaction.type.name, style = MaterialTheme.typography.titleMedium); Text(Money.paiseToRupees(transaction.amountPaise)); Text(transaction.counterparty.orEmpty()) }
                    Column { TextButton(onClick = { editing = transaction }) { Text("Edit") }; TextButton(onClick = { viewModel.delete(transaction.id) }) { Text("Delete") } }
                } }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionEditor(transaction: Transaction?, state: TransactionsUiState, onSave: (Transaction) -> Unit, onCancel: () -> Unit) {
    var type by remember(transaction) { mutableStateOf(transaction?.type ?: TransactionType.EXPENSE) }
    var amount by remember(transaction) { mutableStateOf(transaction?.amountPaise?.let { Money.paiseToRupees(it).removePrefix("INR ").trim() }.orEmpty()) }
    var accountId by remember(transaction, state.accounts) { mutableStateOf(transaction?.accountId ?: state.accounts.firstOrNull()?.id.orEmpty()) }
    var destinationId by remember(transaction) { mutableStateOf(transaction?.destinationAccountId.orEmpty()) }
    var categoryId by remember(transaction) { mutableStateOf(transaction?.categoryId.orEmpty()) }
    var timestamp by remember(transaction) { mutableStateOf((transaction?.timestampMillis ?: System.currentTimeMillis()).toString()) }
    var counterparty by remember(transaction) { mutableStateOf(transaction?.counterparty.orEmpty()) }
    var referenceNumber by remember(transaction) { mutableStateOf(transaction?.referenceNumber.orEmpty()) }
    var note by remember(transaction) { mutableStateOf(transaction?.note.orEmpty()) }
    val categories: List<Category> = if (type == TransactionType.INCOME) state.incomeCategories else state.expenseCategories
    Card(Modifier.fillMaxWidth().padding(vertical = 12.dp)) { Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
        Text(if (transaction == null) "New transaction" else "Edit transaction")
        Row { TransactionType.entries.forEach { entry -> TextButton(onClick = { type = entry; categoryId = ""; if (entry != TransactionType.TRANSFER) destinationId = "" }) { Text(entry.name) } } }
        OutlinedTextField(amount, { amount = it }, label = { Text("Amount (₹)") }, modifier = Modifier.fillMaxWidth())
        SelectionField("Account", state.accounts.map { it.id to it.name }, accountId) { accountId = it }
        if (type == TransactionType.TRANSFER) SelectionField("Destination", state.accounts.filter { it.id != accountId }.map { it.id to it.name }, destinationId) { destinationId = it }
        if (type != TransactionType.TRANSFER) SelectionField("Category", categories.map { it.id to it.name }, categoryId) { categoryId = it }
        OutlinedTextField(timestamp, { timestamp = it }, label = { Text("Date-time (epoch millis)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(counterparty, { counterparty = it }, label = { Text("Merchant / counterparty") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(referenceNumber, { referenceNumber = it }, label = { Text("Reference number") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
        val amountResult = Money.rupeesToPaise(amount)
        val valid = amountResult.isSuccess && accountId.isNotBlank() && timestamp.toLongOrNull() != null && (type == TransactionType.TRANSFER && destinationId.isNotBlank() && destinationId != accountId || type != TransactionType.TRANSFER && categoryId.isNotBlank())
        Row { Button(onClick = {
            amountResult.fold(
                onFailure = { /* validation error - button disabled */ },
                onSuccess = { amountPaise ->
                    onSave(Transaction(
                        transaction?.id ?: UUID.randomUUID().toString(),
                        type, amountPaise, accountId,
                        destinationId.takeIf { type == TransactionType.TRANSFER && it.isNotBlank() },
                        categoryId.takeIf { type != TransactionType.TRANSFER && it.isNotBlank() },
                        timestamp.toLong(),
                        counterparty.ifBlank { null },
                        referenceNumber.ifBlank { null },
                        note.ifBlank { null },
                        TransactionSource.MANUAL
                    ))
                }
            )
        }, enabled = valid) { Text("Save") }; TextButton(onClick = onCancel) { Text("Cancel") } }
    } }
}

@Composable
private fun SelectionField(label: String, options: List<Pair<String, String>>, selectedId: String, onSelected: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label)
        options.forEach { (id, name) -> TextButton(onClick = { onSelected(id) }) { Text(if (id == selectedId) "• $name" else name) } }
        HorizontalDivider()
    }
}
