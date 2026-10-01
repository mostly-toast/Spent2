package com.aditya.spent.data.db

import com.aditya.spent.model.Account
import com.aditya.spent.model.Category
import com.aditya.spent.model.Transaction
import com.aditya.spent.model.TransactionSource
import com.aditya.spent.model.TransactionType

fun AccountEntity.toModel() = Account(id, name, bank, type, lastFourDigits, openingBalancePaise, currentBalancePaise)
fun Account.toEntity() = AccountEntity(id, name, bank, type, lastFourDigits, openingBalancePaise, currentBalancePaise)
fun CategoryEntity.toModel() = Category(id, name, TransactionType.valueOf(type), isArchived)
fun TransactionEntity.toModel() = Transaction(id, TransactionType.valueOf(type), amountPaise, accountId, destinationAccountId, categoryId, timestampMillis, counterparty, referenceNumber, note, TransactionSource.valueOf(source), originalSmsId)
fun Transaction.toEntity() = TransactionEntity(id, type.name, amountPaise, accountId, destinationAccountId, categoryId, timestampMillis, counterparty, referenceNumber, note, source.name, originalSmsId)
