package com.serhii.tvmarketplace.navigation

import androidx.compose.runtime.saveable.SaverScope
import kotlin.test.Test
import kotlin.test.assertEquals

class DestinationSaverTest {

  private val scope = SaverScope { true }

  @Test
  fun `a destination survives saving, arguments included`() {
    for (destination in listOf(Destination.Inbox, Destination.Preview(linkId = 7), Destination.Preview(owner = "a", repo = "b"))) {
      val saved = with(DestinationSaver) { scope.save(destination) }
      assertEquals(destination, DestinationSaver.restore(checkNotNull(saved)))
    }
  }
}
