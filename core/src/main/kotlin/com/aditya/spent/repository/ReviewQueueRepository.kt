package com.aditya.spent.repository

import com.aditya.spent.model.ReviewItem
import kotlinx.coroutines.flow.Flow

interface ReviewQueueRepository {
    suspend fun add(item: ReviewItem)
    fun observeAll(): Flow<List<ReviewItem>>
    suspend fun remove(id: String)
}