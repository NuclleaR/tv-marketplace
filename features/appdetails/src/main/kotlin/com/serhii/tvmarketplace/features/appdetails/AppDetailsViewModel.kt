package com.serhii.tvmarketplace.features.appdetails

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.serhii.tvmarketplace.core.common.DeviceAbis
import com.serhii.tvmarketplace.core.common.TimeSource
import com.serhii.tvmarketplace.core.data.ReleasesRepository
import com.serhii.tvmarketplace.core.data.TrackedAppsRepository
import com.serhii.tvmarketplace.core.data.UpdateChecker
import com.serhii.tvmarketplace.core.domain.AppPackage
import com.serhii.tvmarketplace.core.domain.AppStatus
import com.serhii.tvmarketplace.core.installer.AppState
import com.serhii.tvmarketplace.core.installer.Downloads
import com.serhii.tvmarketplace.core.installer.InstallPermission
import com.serhii.tvmarketplace.core.installer.InstallQueue
import com.serhii.tvmarketplace.core.installer.InstallRequest
import com.serhii.tvmarketplace.core.installer.InstalledApps
import com.serhii.tvmarketplace.core.installer.Installer
import com.serhii.tvmarketplace.core.installer.appState
import com.serhii.tvmarketplace.features.appdetails.AppDetailsViewState.Dialog
import com.serhii.tvmarketplace.features.appdetails.AppDetailsViewState.PackageTag
import com.serhii.tvmarketplace.features.appdetails.AppDetailsViewState.PrimaryAction
import com.serhii.tvmarketplace.navigation.Destination
import com.serhii.tvmarketplace.navigation.Navigator
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

// stored releases older than this are asked for again when the screen opens
private val REFRESH_AFTER: Duration = Duration.ofMinutes(10)

@AssistedInject
class AppDetailsViewModel(
  private val trackedApps: TrackedAppsRepository,
  private val releases: ReleasesRepository,
  private val installedApps: InstalledApps,
  private val downloads: Downloads,
  private val queue: InstallQueue,
  private val permission: InstallPermission,
  private val installer: Installer,
  private val updateChecker: UpdateChecker,
  private val deviceAbis: DeviceAbis,
  private val timeSource: TimeSource,
  private val navigator: Navigator,
  // work that outlives the screen: a check, a removal
  private val appScope: CoroutineScope,
  @Assisted private val appId: Long,
) {

  private var checking by mutableStateOf(false)
  private var dialog by mutableStateOf<Dialog?>(null)
  private var latestState: AppState? = null

  // null while loading; a state with a null app once it's gone
  private fun states(): Flow<Loaded> {
    val abis = deviceAbis.abis()
    val stored = combine(trackedApps.observe(appId), releases.latestReleases(), releases.installRecords()) { app, releases, records ->
      Triple(app, releases[appId], records[appId])
    }
    return combine(
      stored,
      installedApps.changes(),
      downloads.observe(appId),
      queue.jobs.map {
        it[appId]
      },
    ) { (app, release, record), _, download, job ->
      if (app == null) {
        Loaded(null)
      } else {
        val installed = app.packageName?.let(installedApps::get)
        Loaded(appState(app, release, record, installed, download, job, abis), releaseCheckedAt = release?.checkedAt)
      }
    }
  }

  private data class Loaded(
    val state: AppState?,
    val releaseCheckedAt: Instant? = null,
  )

  @Composable
  fun viewState(): AppDetailsViewState {
    val loaded by remember { states() }.collectAsState(initial = null)
    val current = loaded ?: return AppDetailsViewState.Loading
    val state = current.state ?: return AppDetailsViewState.Missing
    latestState = state
    return content(state)
  }

  private fun content(state: AppState): AppDetailsViewState.Content {
    val choice = state.choice
    val chosen = choice?.chosen
    val userChoice = state.app.packagePattern != null && choice != null && !choice.choiceLost
    return AppDetailsViewState.Content(
      name = state.app.displayName,
      ownerAndRepo = state.app.ref.label,
      iconUrl = state.app.iconUrl,
      packageName = state.app.packageName,
      description = state.app.description,
      status = state.status,
      primary = primaryAction(state),
      failure = state.failure,
      latest = state.release?.let { AppDetailsViewState.Latest(it.tag, it.publishedAt) },
      checking = checking && state.release == null,
      packages = choice?.candidates.orEmpty().map { candidate ->
        AppDetailsViewState.PackageRow(
          id = candidate.pkg.id,
          fileName = candidate.pkg.fileName,
          sizeBytes = candidate.pkg.sizeBytes,
          tag = when {
            !candidate.compatible -> PackageTag.NOT_FOR_DEVICE
            userChoice && candidate.pkg.id == chosen?.id -> PackageTag.YOUR_CHOICE
            candidate.pkg.id == choice?.recommended?.id -> PackageTag.RECOMMENDED
            else -> null
          },
          compatible = candidate.compatible,
        )
      },
      choiceLost = choice?.choiceLost == true,
      userChoice = userChoice,
      notesHtml = state.release?.notesHtml,
      needsPermission = state.needsPermission,
      installed = state.installed != null,
      includePrereleases = state.app.includePrereleases,
      dialog = dialog,
    )
  }

  private fun primaryAction(state: AppState): PrimaryAction = when {
    state.failure != null -> PrimaryAction.RETRY
    else -> when (val status = state.status) {
      AppStatus.Unavailable -> PrimaryAction.REMOVE
      is AppStatus.Downloading, AppStatus.Installing -> PrimaryAction.CANCEL
      AppStatus.ReadyToInstall -> PrimaryAction.INSTALL_NOW
      AppStatus.NotInstalled -> PrimaryAction.INSTALL
      is AppStatus.UpdateAvailable -> PrimaryAction.UPDATE
      is AppStatus.UpToDate -> PrimaryAction.OPEN
      AppStatus.NoRelease -> PrimaryAction.CHECK
      is AppStatus.NoPackage -> if (status.installedVersion != null) PrimaryAction.OPEN else PrimaryAction.RELEASE_NOTES
    }
  }

  // the screen came into view: a stale release is asked for again, and an install that waited for the
  // permission goes on once it's granted
  fun onVisible() {
    appScope.launch {
      val stored = releases.latest(appId)
      if (stored == null || Duration.between(stored.checkedAt, timeSource.now()) > REFRESH_AFTER) checkNow()
    }
    val state = latestState ?: return
    if (state.needsPermission && permission.granted()) install(state.choice?.chosen ?: return, state)
  }

  private suspend fun checkNow() {
    checking = true
    try {
      val _ = updateChecker.check(appId)
    } finally {
      checking = false
    }
  }

  fun onPrimary() {
    val state = latestState ?: return
    when (primaryAction(state)) {
      PrimaryAction.INSTALL, PrimaryAction.UPDATE, PrimaryAction.INSTALL_NOW -> install(state.choice?.chosen ?: return, state)
      PrimaryAction.RETRY -> onRetry()
      PrimaryAction.OPEN -> state.installed?.let { val _ = installedApps.launch(it.packageName) }
      PrimaryAction.CANCEL -> queue.cancel(appId)
      PrimaryAction.RELEASE_NOTES -> onReleaseNotes()
      PrimaryAction.REMOVE -> onRemove()
      PrimaryAction.CHECK -> appScope.launch { checkNow() }
    }
  }

  fun onPackage(id: Long) {
    val state = latestState ?: return
    val candidate = state.choice?.candidates?.firstOrNull { it.pkg.id == id } ?: return
    if (!candidate.compatible) return
    install(candidate.pkg, state)
  }

  private fun install(
    pkg: AppPackage,
    state: AppState,
  ) {
    val release = state.release ?: return
    queue.install(InstallRequest(appId, release, pkg, recommended = pkg.id == state.choice?.recommended?.id))
  }

  fun onRetry() {
    val state = latestState ?: return
    queue.dismiss(appId)
    // a failed download stays in the download history until forgotten
    if (state.failure != null) downloads.cancel(appId)
    install(state.choice?.chosen ?: return, state)
  }

  fun permissionIntents(): List<Intent> = permission.settingsIntents()

  fun onPermissionLater() {
    queue.dismiss(appId)
  }

  fun onUseAutomaticChoice() {
    appScope.launch { trackedApps.setPackagePattern(appId, null) }
  }

  fun onReleaseNotes() {
    navigator.goTo(Destination.ReleaseNotes(appId))
  }

  fun onSettings() {
    dialog = Dialog.SETTINGS
  }

  fun onRemove() {
    dialog = Dialog.REMOVE
  }

  fun onDismissDialog() {
    dialog = null
  }

  // what counts as an update changes, so the stored answer and its etag no longer apply
  fun onTogglePrereleases() {
    val state = latestState ?: return
    appScope.launch {
      trackedApps.setIncludePrereleases(appId, !state.app.includePrereleases)
      releases.forgetEtag(appId)
      val _ = updateChecker.check(appId)
    }
  }

  // the app leaves the list whatever the uninstall prompt's answer: declining it only keeps the app on the TV
  fun onConfirmRemove(uninstall: Boolean) {
    val state = latestState ?: return
    dialog = null
    appScope.launch {
      queue.cancel(appId)
      val packageName = state.installed?.packageName
      if (uninstall && packageName != null) {
        val _ = installer.uninstall(packageName)
      }
      trackedApps.remove(appId)
      navigator.goBack()
    }
  }

  @AssistedFactory
  interface Factory {
    fun create(appId: Long): AppDetailsViewModel
  }
}
