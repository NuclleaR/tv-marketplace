package com.serhii.tvmarketplace.core.testing

import android.content.Intent
import com.serhii.tvmarketplace.core.domain.AppPackage
import com.serhii.tvmarketplace.core.domain.InstalledApp
import com.serhii.tvmarketplace.core.installer.Download
import com.serhii.tvmarketplace.core.installer.DownloadState
import com.serhii.tvmarketplace.core.installer.Downloads
import com.serhii.tvmarketplace.core.installer.InstallJob
import com.serhii.tvmarketplace.core.installer.InstallPermission
import com.serhii.tvmarketplace.core.installer.InstallQueue
import com.serhii.tvmarketplace.core.installer.InstallRequest
import com.serhii.tvmarketplace.core.installer.InstalledApps
import com.serhii.tvmarketplace.core.installer.UpdateBatch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

// what the TV has installed; changing apps emits a change, as the package broadcasts do
class FakeInstalledApps(vararg installed: InstalledApp) : InstalledApps {
  val apps = MutableStateFlow(installed.associateBy { it.packageName })
  private val changes = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

  override fun get(packageName: String): InstalledApp? = apps.value[packageName]

  override fun changes(): Flow<Unit> = changes

  val launched = mutableListOf<String>()

  override fun launch(packageName: String): Boolean {
    launched += packageName
    return packageName in apps.value
  }

  fun install(app: InstalledApp) {
    apps.value += app.packageName to app
    changes.tryEmit(Unit)
  }

  fun uninstall(packageName: String) {
    apps.value -= packageName
    changes.tryEmit(Unit)
  }
}

fun installedApp(
  packageName: String = "com.example.app",
  versionName: String? = "1.0.0",
  versionCode: Long = 1,
  lastUpdateTime: Instant = Instant.parse("2026-11-01T10:00:00Z"),
  installedByThisApp: Boolean = true,
) = InstalledApp(packageName, versionName, versionCode, lastUpdateTime, installedByThisApp)

class FakeInstallPermission(var granted: Boolean = true) : InstallPermission {
  override fun granted(): Boolean = granted

  override fun settingsIntents(): List<Intent> = emptyList()
}

// downloads whose state a test moves by hand
class FakeDownloads : Downloads {
  val downloads = MutableStateFlow<Map<Long, Download>>(emptyMap())
  val started = mutableListOf<Pair<Long, AppPackage>>()

  override fun start(
    appId: Long,
    pkg: AppPackage,
    expectedPackage: String?,
  ) {
    started += appId to pkg
    downloads.value += appId to Download(appId, pkg.id, DownloadState.Queued)
  }

  override fun cancel(appId: Long) {
    downloads.value -= appId
  }

  override fun observe(appId: Long): Flow<Download?> = downloads.map { it[appId] }

  override fun observeAll(): Flow<Map<Long, Download>> = downloads

  fun move(
    appId: Long,
    state: DownloadState,
  ) {
    val download = checkNotNull(downloads.value[appId])
    downloads.value += appId to download.copy(state = state)
  }
}

// records what was asked; a test sets the jobs and the batch it wants the screen to see
class FakeInstallQueue : InstallQueue {
  override val jobs = MutableStateFlow<Map<Long, InstallJob>>(emptyMap())
  override val batch = MutableStateFlow<UpdateBatch?>(null)
  val installs = mutableListOf<InstallRequest>()
  val updateAlls = mutableListOf<List<InstallRequest>>()
  val cancelled = mutableListOf<Long>()
  var stopped = false

  override fun install(request: InstallRequest) {
    installs += request
    jobs.value += request.appId to InstallJob.Downloading(null)
  }

  override fun updateAll(requests: List<InstallRequest>) {
    updateAlls += requests
    batch.value = UpdateBatch(total = requests.size, done = 0, failed = emptyMap(), stopping = false, running = true)
  }

  override fun stopAfterCurrent() {
    stopped = true
  }

  override fun cancel(appId: Long) {
    cancelled += appId
    jobs.value -= appId
  }

  override fun dismiss(appId: Long) {
    jobs.value -= appId
  }

  override fun dismissBatch() {
    batch.value = null
  }
}
