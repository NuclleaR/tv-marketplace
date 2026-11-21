package com.serhii.tvmarketplace.core.domain

import java.time.Instant

// where a tracked app's releases come from; GitHub is the only kind for now, F-Droid is the planned next one
sealed interface AppSource {
  // persisted in tracked_app.sourceKind: never change a kind's key
  val kind: String

  data class GitHub(
    val owner: String,
    val repo: String,
  ) : AppSource {
    override val kind: String get() = KIND

    companion object {
      const val KIND = "github"
    }
  }
}

data class Release(
  val tag: String,
  val name: String?,
  val publishedAt: Instant,
  val prerelease: Boolean,
  // already rendered by the source; shown without links, since nothing on a TV can follow them
  val notesHtml: String?,
  // installable files only, in the release's order
  val packages: List<AppPackage>,
) {
  val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: tag
}

// an installable file of a release; named AppPackage because java.lang.Package is imported everywhere
data class AppPackage(
  val id: Long,
  val fileName: String,
  val sizeBytes: Long,
  val downloadUrl: String,
  val sha256: String? = null,
)

data class InstalledApp(
  val packageName: String,
  val versionName: String?,
  val versionCode: Long,
  val lastUpdateTime: Instant,
  // this app is the installer of record, so it may update the app without asking again where the system allows
  val installedByThisApp: Boolean,
)

// why an install or a check didn't go through; shown over the app's status until Retry or dismissal
sealed interface FailureReason {
  data object Network : FailureReason

  data class RateLimited(val resumeAt: Instant) : FailureReason

  data class NoSpace(val neededBytes: Long) : FailureReason

  // incomplete, not matching its checksum, not an APK, or served from somewhere unexpected
  data object Corrupt : FailureReason

  // the package is another app than the one installed under this entry
  data object DifferentApp : FailureReason

  // the installed app is signed with another key, so the system won't update it in place
  data object SignatureConflict : FailureReason

  // the same app is installed at a higher version code, maybe only in another user profile, and the system won't go back
  data object NewerInstalled : FailureReason

  data object Incompatible : FailureReason

  data object CancelledByUser : FailureReason

  data class System(val message: String?) : FailureReason
}
