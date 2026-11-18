package com.serhii.tvmarketplace.features.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.serhii.tvmarketplace.core.data.IncomingLinksRepository
import com.serhii.tvmarketplace.core.data.TrackedAppsRepository
import com.serhii.tvmarketplace.core.linkserver.IncomingLinkEvents
import com.serhii.tvmarketplace.navigation.Destination
import com.serhii.tvmarketplace.navigation.Navigator
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

internal const val NOTICE_DURATION_MS = 8_000L

@Inject
class HomeViewModel(
  private val trackedApps: TrackedAppsRepository,
  private val incomingLinks: IncomingLinksRepository,
  private val events: IncomingLinkEvents,
  private val navigator: Navigator,
) {

  @Composable
  fun viewState(): HomeViewState {
    val apps by remember { trackedApps.trackedApps() }.collectAsState(initial = emptyList())
    val inboxCount by remember { incomingLinks.pending().map { it.size } }.collectAsState(initial = 0)
    var notice by remember { mutableStateOf<HomeViewState.Notice?>(null) }
    LaunchedEffect(Unit) {
      val seen = events.lastReceived.value?.sequence ?: 0
      events.lastReceived.filterNotNull().filter { it.sequence > seen }.collectLatest { link ->
        notice = HomeViewState.Notice(linkId = link.linkId, sender = link.senderLabel)
        delay(NOTICE_DURATION_MS)
        notice = null
      }
    }
    return HomeViewState(
      apps = apps.map { HomeViewState.App(id = it.id, name = it.displayName, ownerAndRepo = "${it.ref.owner}/${it.ref.repo}") },
      inboxCount = inboxCount,
      notice = notice,
    )
  }

  fun onAddApp() {
    navigator.goTo(Destination.AddApp)
  }

  fun onInbox() {
    navigator.goTo(Destination.Inbox)
  }

  fun onSettings() {
    navigator.goTo(Destination.Settings)
  }

  fun onOpenNotice(linkId: Long) {
    navigator.goTo(Destination.Preview(linkId = linkId))
  }
}
