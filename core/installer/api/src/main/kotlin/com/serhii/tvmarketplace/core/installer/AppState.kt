package com.serhii.tvmarketplace.core.installer

import com.serhii.tvmarketplace.core.data.InstallRecord
import com.serhii.tvmarketplace.core.data.StoredRelease
import com.serhii.tvmarketplace.core.data.TrackedApp
import com.serhii.tvmarketplace.core.domain.AppStatus
import com.serhii.tvmarketplace.core.domain.FailureReason
import com.serhii.tvmarketplace.core.domain.InstalledApp
import com.serhii.tvmarketplace.core.domain.PackageChoice
import com.serhii.tvmarketplace.core.domain.PackageChooser
import com.serhii.tvmarketplace.core.domain.PackagePattern
import com.serhii.tvmarketplace.core.domain.Release
import com.serhii.tvmarketplace.core.domain.VersionComparator

// everything Home and the details screen know about one tracked app, derived the same way for both
data class AppState(
  val app: TrackedApp,
  val release: Release?,
  // null without a release
  val choice: PackageChoice?,
  val installed: InstalledApp?,
  val status: AppStatus,
  val failure: FailureReason?,
  val needsPermission: Boolean,
) {
  val hasUpdate: Boolean get() = status is AppStatus.UpdateAvailable
}

fun appState(
  app: TrackedApp,
  stored: StoredRelease?,
  record: InstallRecord?,
  installed: InstalledApp?,
  download: Download?,
  job: InstallJob?,
  deviceAbis: List<String>,
): AppState {
  val release = stored?.release
  val choice = release?.let { r ->
    PackageChooser.choose(
      r.packages,
      deviceAbis,
      app.packagePattern?.let { pattern ->
        { pkg -> PackagePattern.matches(pattern, pkg.fileName, r) }
      },
    )
  }
  val activity = when (job) {
    is InstallJob.Downloading -> AppStatus.Activity.Downloading(job.fraction)
    InstallJob.Installing -> AppStatus.Activity.Installing
    InstallJob.NeedsPermission -> AppStatus.Activity.ReadyToInstall
    is InstallJob.Failed -> null
    null -> when (val state = download?.state) {
      DownloadState.Queued -> AppStatus.Activity.Downloading(null)
      is DownloadState.Running -> AppStatus.Activity.Downloading(state.fraction)
      DownloadState.Verifying -> AppStatus.Activity.Downloading(1F)
      is DownloadState.Done -> AppStatus.Activity.ReadyToInstall
      is DownloadState.Failed, null -> null
    }
  }
  val failure = (job as? InstallJob.Failed)?.reason ?: (download?.state as? DownloadState.Failed)?.reason?.takeIf { job == null }
  // what this app installed is known exactly while the TV still has that version
  val installedTag = record?.takeIf { installed != null && installed.versionCode == it.versionCode }?.tag
  val status = AppStatus.of(
    sourceGone = app.sourceGone,
    activity = activity,
    latest = release,
    hasCompatiblePackage = choice?.chosen != null,
    installed = installed,
  ) { latest, app -> VersionComparator.isNewer(latest, app, installedTag) }
  return AppState(app, release, choice, installed, status, failure, needsPermission = job == InstallJob.NeedsPermission)
}
