package com.serhii.tvmarketplace.core.linkserver

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NsdAdvertiserTest {

  private class FakeRegistrar : ServiceRegistrar {
    val registered = mutableListOf<Advertisement>()
    var unregistered = 0

    override fun register(advertisement: Advertisement) {
      registered += advertisement
    }

    override fun unregister() {
      unregistered++
    }
  }

  @Test
  fun `advertises the running server with the api version and the install id`() = runTest {
    val state = MutableStateFlow<ServerState>(ServerState.Stopped)
    val registrar = FakeRegistrar()
    val advertiser = NsdAdvertiser(registrar, installId = { "install-1" }, tvName = { "Living room TV" })
    advertiser.start(backgroundScope, state)
    runCurrent()
    assertEquals(emptyList(), registrar.registered)
    state.value = ServerState.Running("192.168.1.42", 8765)
    runCurrent()
    assertEquals(
      listOf(Advertisement(name = "Living room TV", type = "_tvmarket._tcp.", port = 8765, txt = mapOf("v" to "1", "id" to "install-1"))),
      registrar.registered,
    )
    state.value = ServerState.NoNetwork
    runCurrent()
    assertEquals(1, registrar.unregistered)
  }
}
