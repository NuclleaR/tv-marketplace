package com.serhii.tvmarketplace.core.data

import com.serhii.tvmarketplace.core.domain.AppPreview
import com.serhii.tvmarketplace.core.domain.AppSource
import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class TrackedApp(
  val id: Long,
  val ref: AppSource,
  val displayName: String,
  val latestVersion: String?,
  val avatarUrl: String?,
  val addedAt: Instant,
  // the app's own icon, when its source has one
  val iconUrl: String? = null,
  // learned from the first APK or manifest read; null until then
  val packageName: String? = null,
  val includePrereleases: Boolean = false,
  // the package the user picked, as a version-independent file name pattern; null means the automatic choice
  val packagePattern: String? = null,
  // the source answered "not found" on the last check
  val sourceGone: Boolean = false,
  val description: String? = null,
) {
  val source: AppSource get() = ref
}

interface TrackedAppsRepository {
  fun trackedApps(): Flow<List<TrackedApp>>

  suspend fun find(ref: AppSource): TrackedApp?

  // adding an app that is already tracked returns the existing one
  suspend fun add(preview: AppPreview): TrackedApp

  // apps added before icons were looked up; setIcon marks one as looked up, with or without an icon
  suspend fun withUncheckedIcon(): List<TrackedApp>

  suspend fun setIcon(
    id: Long,
    iconUrl: String?,
  )

  suspend fun get(id: Long): TrackedApp?

  fun observe(id: Long): Flow<TrackedApp?>

  // a known package name is never replaced: another name means another app
  suspend fun setPackageName(
    id: Long,
    packageName: String,
  )

  suspend fun setIncludePrereleases(
    id: Long,
    include: Boolean,
  )

  suspend fun setPackagePattern(
    id: Long,
    pattern: String?,
  )

  suspend fun setSourceGone(
    id: Long,
    gone: Boolean,
  )

  // stops tracking; whatever was stored about the app goes with it, the installed app stays
  suspend fun remove(id: Long)
}
