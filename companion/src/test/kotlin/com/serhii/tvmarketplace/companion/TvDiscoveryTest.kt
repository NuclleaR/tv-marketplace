package com.serhii.tvmarketplace.companion

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TvDiscoveryTest {

  private val found = MutableStateFlow<List<FoundTv>>(emptyList())
  private val discovery = TvDiscovery { found }

  @Test
  fun `finds a paired TV by its id at its current address`() = runTest {
    var result: FoundTv? = null
    val _ = launch { result = discovery.find(id = "tv-1") }
    runCurrent()
    found.value = listOf(FoundTv("Bedroom", "tv-2", "192.168.1.7", 8765), FoundTv("Living room", "tv-1", "192.168.1.42", 8766))
    runCurrent()
    assertEquals(FoundTv("Living room", "tv-1", "192.168.1.42", 8766), result)
  }

  @Test
  fun `gives up after 10 seconds`() = runTest {
    var result: FoundTv? = FoundTv("x", "x", "x", 1)
    val _ = launch { result = discovery.find(id = "tv-1") }
    advanceTimeBy(DISCOVERY_TIMEOUT_MS + 1)
    assertNull(result)
  }
}
