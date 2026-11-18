package com.serhii.tvmarketplace.core.data.impl

import com.serhii.tvmarketplace.core.data.Item
import com.serhii.tvmarketplace.core.data.ItemRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

// replace with Room, DataStore or a network source; consumers only see ItemRepository
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class InMemoryItemRepository : ItemRepository {

  private val items = MutableStateFlow(
    (1..12).map { index ->
      Item(id = index.toString(), title = "Item $index", description = "The description of item $index.")
    },
  )

  override fun items(): Flow<List<Item>> = items

  override fun item(id: String): Flow<Item?> = items.map { items -> items.firstOrNull { it.id == id } }
}
