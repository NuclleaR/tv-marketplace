package com.serhii.tvmarketplace.core.data.impl

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InMemoryItemRepositoryTest {

  @Test
  fun `finds an item by its id`() = runTest {
    val repository = InMemoryItemRepository()
    assertEquals("Item 3", repository.item("3").first()?.title)
  }
}
