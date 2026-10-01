package com.aditya.spent.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val bank: String,
    val type: String,
    val lastFourDigits: String,
    val openingBalancePaise: Long,
    val currentBalancePaise: Long,
)

@Entity(tableName = "categories", indices = [Index(value = ["type", "name"], unique = true)])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val isArchived: Boolean,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["destinationAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["accountId", "referenceNumber"]), Index(value = ["accountId", "timestampMillis"]), Index(value = ["destinationAccountId"]), Index(value = ["categoryId"])],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val type: String,
    val amountPaise: Long,
    val accountId: String,
    val destinationAccountId: String?,
    val categoryId: String?,
    val timestampMillis: Long,
    val counterparty: String?,
    val referenceNumber: String?,
    val note: String?,
    val source: String,
    val originalSmsId: String?,
)
