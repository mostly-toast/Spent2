package com.aditya.spent.di

import android.content.Context
import androidx.room.Room
import com.aditya.spent.data.db.SpentDatabase
import com.aditya.spent.data.repo.RoomAccountRepository
import com.aditya.spent.data.repo.RoomCategoryRepository
import com.aditya.spent.data.repo.RoomTransactionRepository
import com.aditya.spent.repository.AccountRepository
import com.aditya.spent.repository.CategoryRepository
import com.aditya.spent.repository.TransactionRepository
import com.aditya.spent.usecase.TransactionBalanceAdjustments

class AppContainer(context: Context) {
    private val database = Room.databaseBuilder(context, SpentDatabase::class.java, "spent.db")
        .addCallback(SpentDatabase.callback)
        .build()
    val accountRepository: AccountRepository = RoomAccountRepository(database, database.accountDao(), database.transactionDao())
    val categoryRepository: CategoryRepository = RoomCategoryRepository(database.categoryDao())
    val transactionRepository: TransactionRepository = RoomTransactionRepository(database, database.accountDao(), database.categoryDao(), database.transactionDao(), TransactionBalanceAdjustments())
}
