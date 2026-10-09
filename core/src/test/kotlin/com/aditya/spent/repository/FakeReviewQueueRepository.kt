package com.aditya.spent.repository

import com.aditya.spent.model.ReviewItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeReviewQueueRepository : ReviewQueueRepository {
    private val _items = MutableStateFlow<List<ReviewItem>>(emptyList())
    override fun observeAll(): Flow<List<ReviewItem>> = _items.asStateFlow()

    override suspend fun add(item: ReviewItem) {
        _items.value = _items.value + item
    }

    override suspend fun remove(id: String) {
        _items.value = _items.value.filterNot { it.id == id }
    }
}