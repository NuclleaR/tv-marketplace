package com.serhii.tvmarketplace.features.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.serhii.tvmarketplace.core.common.DeviceAbis
import com.serhii.tvmarketplace.core.data.IncomingLinksRepository
import com.serhii.tvmarketplace.core.data.ReleasesRepository
import com.serhii.tvmarketplace.core.data.TrackedAppsRepository
import com.serhii.tvmarketplace.core.data.UpdateCheckState
import com.serhii.tvmarketplace.core.data.UpdateChecker
import com.serhii.tvmarketplace.core.installer.AppState
import com.serhii.tvmarketplace.core.installer.Downloads
import com.serhii.tvmarketplace.core.installer.InstallQueue
import com.serhii.tvmarketplace.core.installer.InstallRequest
import com.serhii.tvmarketplace.core.installer.InstalledApps
import com.serhii.tvmarketplace.core.installer.appState
import com.serhii.tvmarketplace.core.linkserver.IncomingLinkEvents
import com.serhii.tvmarketplace.navigation.Destination
import com.serhii.tvmarketplace.navigation.Navigator
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal const val NOTICE_DURATION_MS = 8_000L

@Inject
class HomeViewModel(
  private val trackedApps: TrackedAppsRepository,
  private val incomingLinks: IncomingLinksRepository,
  private val events: IncomingLinkEvents,
  private val navigator: Navigator,
  private val releases: ReleasesRepository,
  private val installedApps: InstalledApps,
  private val downloads: Downloads,
  private val queue: InstallQueue,
  private val updateChecker: UpdateChecker,
  private val deviceAbis: DeviceAbis,
  // a check outlives the screen that started it
  private val appScope: CoroutineScope,
) {

  private var states: List<AppState> = emptyList()

  private fun appStates(): Flow<List<AppState>> {
    val abis = deviceAbis.abis()
    val stored = combine(trackedApps.trackedApps(), releases.latestReleases(), releases.installRecords()) { apps, releases, records ->
      Triple(apps, releases, records)
    }
    return combine(stored, installedApps.changes(), downloads.observeAll(), queue.jobs) { (apps, releases, records), _, downloads, jobs ->
      apps.map { app ->
        val installed = app.packageName?.let(installedApps::get)
        appState(app, releases[app.id], records[app.id], installed, downloads[app.id], jobs[app.id], abis)
      }
    }
  }

  @Composable
  fun viewState(): HomeViewState {
    val apps by remember { appStates() }.collectAsState(initial = emptyList())
    val check by remember {
      updateChecker.state()
    }.collectAsState(initial = UpdateCheckState(running = false, lastFinishedAt = null, resumeAt = null))
    val batch by queue.batch.collectAsState()
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
    states = apps
    return HomeViewState(
      apps = apps.map {
        HomeViewState.App(
          id = it.app.id,
          name = it.app.displayName,
          ownerAndRepo = "${it.app.ref.owner}/${it.app.ref.repo}",
          iconUrl = it.app.iconUrl,
          packageName = it.app.packageName,
          status = it.status,
        )
      },
      inboxCount = inboxCount,
      notice = notice,
      updates = HomeViewState.Updates(
        count = apps.count { it.hasUpdate },
        checking = check.running,
        lastCheckedAt = check.lastFinishedAt,
        resumeAt = check.resumeAt,
        batch = batch,
      ),
    )
  }

  fun onOpen(destination: Destination) {
    navigator.goTo(destination)
  }

  fun onOpenNotice(linkId: Long) {
    navigator.goTo(Destination.Preview(linkId = linkId))
  }

  fun onCheckForUpdates() {
    appScope.launch { val _ = updateChecker.checkAll() }
  }

  // only what the check found newer, each with the package it would pick for that app
  fun onUpdateAll() {
    val requests = states.filter { it.hasUpdate }.mapNotNull { state ->
      val release = state.release ?: return@mapNotNull null
      val pkg = state.choice?.chosen ?: return@mapNotNull null
      InstallRequest(state.app.id, release, pkg, recommended = pkg.id == state.choice?.recommended?.id)
    }
    queue.updateAll(requests)
  }

  fun onStopAfterCurrent() {
    queue.stopAfterCurrent()
  }

  fun onShowFailures() {
    navigator.goTo(Destination.UpdateFailures)
  }
}
