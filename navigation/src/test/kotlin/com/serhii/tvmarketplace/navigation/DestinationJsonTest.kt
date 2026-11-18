package com.serhii.tvmarketplace.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class DestinationJsonTest {

  @Test
  fun `decodes a back stack by short names`() {
    val backStack = decodeBackStack("""[{"type":"Home"},{"type":"Detail","itemId":"1"}]""")
    assertEquals(listOf(Destination.Home, Destination.Detail(itemId = "1")), backStack)
  }
}
