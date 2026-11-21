package com.serhii.tvmarketplace.core.domain

// what Home and the details screen show for a tracked app, and which action comes first
sealed interface AppStatus {
  // the source said the repository is gone; the installed app, if any, is kept
  data object Unavailable : AppStatus

  // fraction is null while the size isn't known yet
  data class Downloading(val fraction: Float?) : AppStatus

  // the package is downloaded and verified; it waits for the user to confirm the system prompt
  data object ReadyToInstall : AppStatus

  // handed to the system: its prompt or its result is pending
  data object Installing : AppStatus

  // the latest eligible release has nothing that runs here; installedVersion is null when not installed
  data class NoPackage(val installedVersion: String?) : AppStatus

  data object NotInstalled : AppStatus

  // not installed, and the source has no release for it yet, or hasn't been asked yet
  data object NoRelease : AppStatus

  data class UpdateAvailable(
    val installedVersion: String?,
    val latestVersion: String,
  ) : AppStatus

  data class UpToDate(val installedVersion: String?) : AppStatus

  // what is being done for the app right now, ahead of what is installed
  sealed interface Activity {
    data class Downloading(val fraction: Float?) : Activity

    data object Installing : Activity

    data object ReadyToInstall : Activity
  }

  companion object {

    // the first rule that matches wins; isNewer says whether the latest release is newer than the installed app
    fun of(
      sourceGone: Boolean,
      activity: Activity?,
      latest: Release?,
      hasCompatiblePackage: Boolean,
      installed: InstalledApp?,
      isNewer: (Release, InstalledApp) -> Boolean,
    ): AppStatus {
      val newer = latest != null && installed != null && isNewer(latest, installed)
      return when {
        sourceGone -> Unavailable
        activity is Activity.Downloading -> Downloading(activity.fraction)
        activity == Activity.Installing -> Installing
        activity == Activity.ReadyToInstall -> ReadyToInstall
        installed != null && latest != null && !newer -> UpToDate(installed.versionName)
        installed != null && latest == null -> UpToDate(installed.versionName)
        latest == null -> NoRelease
        !hasCompatiblePackage -> NoPackage(installed?.versionName)
        installed == null -> NotInstalled
        else -> UpdateAvailable(installed.versionName, latest.tag)
      }
    }
  }
}
