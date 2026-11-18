package com.serhii.tvmarketplace.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class DestinationJsonTest {

  @Test
  fun `decodes a back stack by short names`() {
    val backStack = decodeBackStack("""[{"type":"Home"},{"type":"Preview","owner":"a","repo":"b"}]""")
    assertEquals(listOf(Destination.Home, Destination.Preview(owner = "a", repo = "b")), backStack)
  }
}
