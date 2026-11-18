package com.serhii.tvmarketplace.core.data

import com.serhii.tvmarketplace.core.domain.AppPreview
import com.serhii.tvmarketplace.core.domain.GitHubRepoRef
import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class TrackedApp(
  val id: Long,
  val ref: GitHubRepoRef,
  val displayName: String,
  val latestVersion: String?,
  val avatarUrl: String?,
  val addedAt: Instant,
  // the app's own icon from its fastlane metadata, when the repository has one
  val iconUrl: String? = null,
)

interface TrackedAppsRepository {
  fun trackedApps(): Flow<List<TrackedApp>>

  suspend fun find(ref: GitHubRepoRef): TrackedApp?

  // adding an app that is already tracked returns the existing one
  suspend fun add(preview: AppPreview): TrackedApp

  // apps added before icons were looked up; setIcon marks one as looked up, with or without an icon
  suspend fun withUncheckedIcon(): List<TrackedApp>

  suspend fun setIcon(
    id: Long,
    iconUrl: String?,
  )
}
