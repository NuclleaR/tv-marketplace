package com.serhii.tvmarketplace.features.addapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.serhii.tvmarketplace.core.linkserver.IncomingLinkEvents
import com.serhii.tvmarketplace.core.linkserver.LinkServer
import com.serhii.tvmarketplace.core.linkserver.ServerState
import com.serhii.tvmarketplace.core.linkserver.SessionManager
import com.serhii.tvmarketplace.navigation.Destination
import com.serhii.tvmarketplace.navigation.Navigator
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull

internal const val SESSION_CHECK_INTERVAL_MS = 15_000L

@Inject
class AddAppViewModel(
  private val sessions: SessionManager,
  private val server: LinkServer,
  private val events: IncomingLinkEvents,
  private val navigator: Navigator,
) {

  @Composable
  fun viewState(): AddAppViewState {
    // the code on screen is only valid while the screen is
    DisposableEffect(Unit) {
      val _ = sessions.start()
      onDispose { sessions.invalidate() }
    }
    LaunchedEffect(Unit) {
      while (true) {
        delay(SESSION_CHECK_INTERVAL_MS)
        val _ = sessions.refreshIfExpired()
      }
    }
    LaunchedEffect(Unit) {
      val seen = events.lastReceived.value?.sequence ?: 0
      events.lastReceived.filterNotNull().filter { it.sequence > seen }.collect { link ->
        navigator.goTo(Destination.Preview(linkId = link.linkId))
      }
    }
    val session by sessions.current.collectAsState()
    val serverState by server.state.collectAsState()
    return when (val state = serverState) {
      is ServerState.Running -> session?.let {
        AddAppViewState.Ready(
          qrContent = "${state.baseUrl}/s/${it.token}",
          address = state.baseUrl,
          pin = it.pin.chunked(3).joinToString(" "),
        )
      } ?: AddAppViewState.Loading
      ServerState.NoNetwork -> AddAppViewState.NoNetwork
      ServerState.Failed -> AddAppViewState.Failed
      ServerState.Stopped -> AddAppViewState.Loading
    }
  }

  fun onTypeIt() {
    navigator.goTo(Destination.TypeIt)
  }

  // the server restarts by itself when the network comes back; retry re-opens the screen's session
  fun onRetry() {
    val _ = sessions.start()
  }
}
