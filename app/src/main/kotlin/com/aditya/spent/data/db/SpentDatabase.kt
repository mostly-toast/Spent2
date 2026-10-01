package com.aditya.spent.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [AccountEntity::class, CategoryEntity::class, TransactionEntity::class], version = 1, exportSchema = true)
abstract class SpentDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        val callback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                defaultCategories.forEach { (id, name, type) ->
                    db.execSQL("INSERT INTO categories (id, name, type, isArchived) VALUES (?, ?, ?, 0)", arrayOf(id, name, type))
                }
            }
        }

        private val defaultCategories = listOf(
            Triple("4f9db44b-98f0-459f-a8c5-000000000001", "Food", "EXPENSE"),
            Triple("4f9db44b-98f0-459f-a8c5-000000000002", "Travel", "EXPENSE"),
            Triple("4f9db44b-98f0-459f-a8c5-000000000003", "Shopping", "EXPENSE"),
            Triple("4f9db44b-98f0-459f-a8c5-000000000004", "Bills & Utilities", "EXPENSE"),
            Triple("4f9db44b-98f0-459f-a8c5-000000000005", "Health", "EXPENSE"),
            Triple("4f9db44b-98f0-459f-a8c5-000000000006", "Education", "EXPENSE"),
            Triple("4f9db44b-98f0-459f-a8c5-000000000007", "Entertainment", "EXPENSE"),
            Triple("4f9db44b-98f0-459f-a8c5-000000000008", "Other Expense", "EXPENSE"),
            Triple("4f9db44b-98f0-459f-a8c5-000000000009", "Salary", "INCOME"),
            Triple("4f9db44b-98f0-459f-a8c5-000000000010", "Grants", "INCOME"),
            Triple("4f9db44b-98f0-459f-a8c5-000000000011", "Interest", "INCOME"),
            Triple("4f9db44b-98f0-459f-a8c5-000000000012", "Freelance", "INCOME"),
            Triple("4f9db44b-98f0-459f-a8c5-000000000013", "Other Income", "INCOME"),
        )
    }
}
