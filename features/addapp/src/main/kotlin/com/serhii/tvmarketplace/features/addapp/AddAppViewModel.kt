package com.serhii.tvmarketplace.features.addapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.serhii.tvmarketplace.core.data.FDroidCatalog
import com.serhii.tvmarketplace.core.data.GitHubRepository
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
  gitHub: GitHubRepository,
  fDroid: FDroidCatalog,
) {

  private val search = GitHubSearch(gitHub, fDroid)

  private var results by mutableStateOf<SearchResults>(SearchResults.None)
  private var searching by mutableStateOf(false)

  // the TV app may sit in the background on this screen: no valid code and no jumps to a preview meanwhile
  private var visible = true

  // the query lives with the screen (rememberSaveable), which outlives this view model when a preview is opened on top
  @Composable
  fun viewState(query: String): AddAppViewState {
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
        if (visible) navigator.goTo(Destination.Preview(linkId = link.linkId))
      }
    }
    LaunchedEffect(query) {
      val parsed = SearchQuery.parse(query)
      searching = parsed != SearchQuery.Empty
      results = search.search(parsed)
      searching = false
    }
    val session by sessions.current.collectAsState()
    val serverState by server.state.collectAsState()
    val phone = when (val state = serverState) {
      is ServerState.Running -> session?.let {
        PhoneState.Ready(
          qrContent = "${state.baseUrl}/s/${it.token}",
          address = state.baseUrl,
          pin = it.pin.chunked(3).joinToString(" "),
        )
      } ?: PhoneState.Loading
      ServerState.NoNetwork -> PhoneState.NoNetwork
      ServerState.Failed -> PhoneState.Failed
      ServerState.Stopped -> PhoneState.Loading
    }
    return AddAppViewState(query = query, phone = phone, results = results, searching = searching)
  }

  fun onRepoClick(repo: SearchResults.Repo) {
    navigator.goTo(Destination.Preview(owner = repo.owner, repo = repo.name))
  }

  fun onFDroidClick(app: SearchResults.FDroidApp) {
    navigator.goTo(Destination.Preview(fdroidPackage = app.packageName))
  }

  fun onHidden() {
    visible = false
    sessions.invalidate()
  }

  fun onVisible() {
    if (visible) return
    visible = true
    val _ = sessions.start()
  }

  // the server restarts by itself when the network comes back; retry re-opens the screen's session
  fun onRetry() {
    val _ = sessions.start()
  }
}
