package com.serhii.tvmarketplace.core.data

import kotlinx.coroutines.flow.Flow

interface ItemRepository {
  fun items(): Flow<List<Item>>
  fun item(id: String): Flow<Item?>
}
