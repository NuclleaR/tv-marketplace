package com.serhii.tvmarketplace.core.installer

import android.content.Intent
import com.serhii.tvmarketplace.core.domain.FailureReason
import com.serhii.tvmarketplace.core.domain.InstalledApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

// what an APK file says about itself
data class ApkInfo(
  val packageName: String,
  val versionName: String?,
  val versionCode: Long,
)

interface PackageInspector {
  // null when the file isn't an APK the system can parse
  fun inspect(file: File): ApkInfo?
}

interface InstalledApps {
  fun get(packageName: String): InstalledApp?

  // emits whenever any app may have been installed, updated or removed, including while this app wasn't running
  fun changes(): Flow<Unit>

  // opens an installed app, the TV launcher entry first; false when it has none
  fun launch(packageName: String): Boolean
}

interface InstallPermission {
  // whether this app may install other apps
  fun granted(): Boolean

  // the system screens that grant it, best first; some TVs lack the first one
  fun settingsIntents(): List<Intent>
}

sealed interface InstallOutcome {
  data class Installed(
    val packageName: String,
    val versionCode: Long,
  ) : InstallOutcome

  // nothing was started: the user has to allow installing apps first
  data object NeedsPermission : InstallOutcome

  data class Failed(val reason: FailureReason) : InstallOutcome
}

sealed interface UninstallOutcome {
  data object Uninstalled : UninstallOutcome

  data object CancelledByUser : UninstallOutcome

  data class Failed(val message: String?) : UninstallOutcome
}

// a system confirmation prompt waiting to be shown; only a visible activity may show it
data class PendingConfirmation(
  val id: Int,
  val intent: Intent,
)

interface Installer {
  // one install runs at a time; the others wait. The system's install prompt is always shown
  suspend fun install(
    file: File,
    expectedPackage: String?,
  ): InstallOutcome

  suspend fun uninstall(packageName: String): UninstallOutcome

  val pendingConfirmation: StateFlow<PendingConfirmation?>

  fun confirmationShown(id: Int)
}
