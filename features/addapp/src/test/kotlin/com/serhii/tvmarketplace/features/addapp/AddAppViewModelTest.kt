package com.serhii.tvmarketplace.features.addapp

import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.turbine.test
import com.serhii.tvmarketplace.core.domain.GitHubRepoRef
import com.serhii.tvmarketplace.core.linkserver.IncomingLinkEvents
import com.serhii.tvmarketplace.core.linkserver.LinkServer
import com.serhii.tvmarketplace.core.linkserver.ServerState
import com.serhii.tvmarketplace.core.linkserver.SessionManager
import com.serhii.tvmarketplace.core.testing.MutableTimeSource
import com.serhii.tvmarketplace.navigation.Destination
import com.serhii.tvmarketplace.navigation.Navigator
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class AddAppViewModelTest {

  private val time = MutableTimeSource()
  private val sessions = SessionManager(time)
  private val serverState = MutableStateFlow<ServerState>(ServerState.Running(ip = "192.168.1.42", port = 8765))
  private val server = mockk<LinkServer> { every { state } returns serverState }
  private val events = IncomingLinkEvents()
  private val navigator = mockk<Navigator>(relaxUnitFun = true)
  private val viewModel = AddAppViewModel(sessions, server, events, navigator)

  @Test
  fun `shows the QR link, the address and the pin of a new session`() = runTest {
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      val ready = assertIs<AddAppViewState.Ready>(awaitMatching { it !is AddAppViewState.Loading })
      val session = checkNotNull(sessions.current.value)
      assertEquals("http://192.168.1.42:8765/s/${session.token}", ready.qrContent)
      assertEquals("http://192.168.1.42:8765", ready.address)
      assertEquals(session.pin.chunked(3).joinToString(" "), ready.pin)
    }
  }

  @Test
  fun `shows the network problems of the server`() = runTest {
    serverState.value = ServerState.NoNetwork
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      assertEquals(AddAppViewState.NoNetwork, awaitMatching { it != AddAppViewState.Loading })
      serverState.value = ServerState.Failed
      assertEquals(AddAppViewState.Failed, awaitItem())
    }
  }

  @Test
  fun `rotates an expired session in place`() = runTest {
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      val first = assertIs<AddAppViewState.Ready>(awaitMatching { it !is AddAppViewState.Loading })
      time.now += Duration.ofMinutes(10)
      advanceTimeBy(SESSION_CHECK_INTERVAL_MS + 1)
      val second = assertIs<AddAppViewState.Ready>(expectMostRecentItem())
      assertNotEquals(first.qrContent, second.qrContent)
    }
  }

  @Test
  fun `opens the preview of a link received while open, not of an older one`() = runTest {
    events.emit(linkId = 1, ref = GitHubRepoRef("old", "link"), senderLabel = "Phone")
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }
    runCurrent()
    verify(exactly = 0) { navigator.goTo(any()) }
    events.emit(linkId = 2, ref = GitHubRepoRef("new", "link"), senderLabel = "Phone")
    runCurrent()
    verify { navigator.goTo(Destination.Preview(linkId = 2)) }
  }

  @Test
  fun `leaving the screen ends the session`() = runTest {
    val job = launch { launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.collect {} }
    runCurrent()
    assertNotEquals(null, sessions.current.value)
    job.cancel()
    runCurrent()
    assertNull(sessions.current.value)
  }

  @Test
  fun `type it instead opens the typing screen`() {
    viewModel.onTypeIt()
    verify { navigator.goTo(Destination.TypeIt) }
  }
}
