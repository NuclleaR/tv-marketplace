package com.serhii.tvmarketplace.core.installer.impl

import com.serhii.tvmarketplace.core.common.TimeSource
import com.serhii.tvmarketplace.core.data.InstallRecord
import com.serhii.tvmarketplace.core.data.ReleasesRepository
import com.serhii.tvmarketplace.core.data.TrackedAppsRepository
import com.serhii.tvmarketplace.core.domain.FailureReason
import com.serhii.tvmarketplace.core.domain.PackagePattern
import com.serhii.tvmarketplace.core.installer.DownloadState
import com.serhii.tvmarketplace.core.installer.Downloads
import com.serhii.tvmarketplace.core.installer.InstallJob
import com.serhii.tvmarketplace.core.installer.InstallOutcome
import com.serhii.tvmarketplace.core.installer.InstallQueue
import com.serhii.tvmarketplace.core.installer.InstallRequest
import com.serhii.tvmarketplace.core.installer.InstalledApps
import com.serhii.tvmarketplace.core.installer.Installer
import com.serhii.tvmarketplace.core.installer.UpdateBatch
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

// download, verify, install, record: for one app on request, or for every update in turn. It runs in the app's
// scope, so leaving the screen that started it doesn't stop it
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DefaultInstallQueue(
  private val downloads: Downloads,
  private val installer: Installer,
  private val installedApps: InstalledApps,
  private val trackedApps: TrackedAppsRepository,
  private val releases: ReleasesRepository,
  private val timeSource: TimeSource,
  private val scope: CoroutineScope,
) : InstallQueue {

  private val jobsState = MutableStateFlow<Map<Long, InstallJob>>(emptyMap())
  private val batchState = MutableStateFlow<UpdateBatch?>(null)
  private val running = ConcurrentHashMap<Long, Job>()
  private var batchJob: Job? = null

  override val jobs: StateFlow<Map<Long, InstallJob>> = jobsState

  override val batch: StateFlow<UpdateBatch?> = batchState

  override fun install(request: InstallRequest) {
    if (running[request.appId]?.isActive == true) return
    running[request.appId] = scope.launch { val _ = run(request) }
  }

  override fun updateAll(requests: List<InstallRequest>) {
    if (batchJob?.isActive == true || requests.isEmpty()) return
    batchState.value = UpdateBatch(total = requests.size, done = 0, failed = emptyMap(), stopping = false, running = true)
    batchJob = scope.launch {
      for (request in requests) {
        if (batchState.value?.stopping == true) break
        val result = run(request)
        batchState.update { batch ->
          batch?.copy(
            done = batch.done + 1,
            failed = if (result is Result.Failed) batch.failed + (request.appId to result.reason) else batch.failed,
          )
        }
        // every next one would ask for the same permission: stop and let the user grant it
        if (result == Result.NeedsPermission) break
      }
      batchState.update { it?.copy(running = false, stopping = false) }
    }
  }

  override fun stopAfterCurrent() {
    batchState.update { if (it?.running == true) it.copy(stopping = true) else it }
  }

  override fun cancel(appId: Long) {
    running.remove(appId)?.cancel()
    downloads.cancel(appId)
    jobsState.update { it - appId }
  }

  override fun dismiss(appId: Long) {
    jobsState.update { jobs -> if (jobs[appId] is InstallJob.Failed || jobs[appId] == InstallJob.NeedsPermission) jobs - appId else jobs }
  }

  override fun dismissBatch() {
    if (batchState.value?.running != true) batchState.value = null
  }

  private sealed interface Result {
    data object Installed : Result

    data object NeedsPermission : Result

    data class Failed(val reason: FailureReason) : Result
  }

  private fun set(
    appId: Long,
    job: InstallJob?,
  ) = jobsState.update { if (job == null) it - appId else it + (appId to job) }

  private suspend fun run(request: InstallRequest): Result {
    val appId = request.appId
    val app = trackedApps.get(appId) ?: return Result.Failed(FailureReason.System(null))
    set(appId, InstallJob.Downloading(null))
    val file = when (val downloaded = download(request, app.packageName)) {
      is DownloadState.Done -> downloaded.file
      is DownloadState.Failed -> return fail(appId, downloaded.reason)
      else -> return fail(appId, FailureReason.System(null))
    }
    set(appId, InstallJob.Installing)
    // the system may skip its prompt only for an app this app installed itself, and only for an update the user started
    val installedByUs = app.packageName?.let(installedApps::get)?.installedByThisApp == true
    return when (val outcome = installer.install(file, app.packageName, allowSilentUpdate = installedByUs)) {
      is InstallOutcome.Installed -> {
        trackedApps.setPackageName(appId, outcome.packageName)
        // picking another package than the recommended one is remembered for the app's updates; the recommended one
        // goes back to the automatic choice
        trackedApps.setPackagePattern(appId, if (request.recommended) null else PackagePattern.of(request.pkg.fileName, request.release))
        releases.recordInstall(
          InstallRecord(appId, outcome.packageName, request.release.tag, outcome.versionCode, request.pkg.fileName, timeSource.now()),
        )
        downloads.cancel(appId)
        set(appId, null)
        Result.Installed
      }
      InstallOutcome.NeedsPermission -> {
        // the download is kept: once allowed, the install continues from it
        set(appId, InstallJob.NeedsPermission)
        Result.NeedsPermission
      }
      is InstallOutcome.Failed -> fail(appId, outcome.reason)
    }
  }

  private fun fail(
    appId: Long,
    reason: FailureReason,
  ): Result {
    set(appId, InstallJob.Failed(reason))
    return Result.Failed(reason)
  }

  // a finished download of the same package is reused; otherwise one is started, and followed until it ends
  private suspend fun download(
    request: InstallRequest,
    expectedPackage: String?,
  ): DownloadState {
    val appId = request.appId
    val current = downloads.observe(appId).first()
    val state = current?.takeIf { it.packageId == request.pkg.id }?.state
    val reusable = state is DownloadState.Done && state.file.exists()
    val inProgress = state is DownloadState.Running || state == DownloadState.Queued
    if (!reusable && !inProgress) downloads.start(appId, request.pkg, expectedPackage)
    val end = downloads.observe(appId)
      .filter { download ->
        if (download == null || download.packageId != request.pkg.id) return@filter false
        when (val state = download.state) {
          is DownloadState.Running -> set(appId, InstallJob.Downloading(state.fraction))
          DownloadState.Verifying -> set(appId, InstallJob.Downloading(1F))
          else -> Unit
        }
        download.state is DownloadState.Done || download.state is DownloadState.Failed
      }
      .first()
    return checkNotNull(end).state
  }
}
