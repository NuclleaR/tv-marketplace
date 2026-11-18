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
)

interface TrackedAppsRepository {
  fun trackedApps(): Flow<List<TrackedApp>>

  suspend fun find(ref: GitHubRepoRef): TrackedApp?

  // adding an app that is already tracked returns the existing one
  suspend fun add(preview: AppPreview): TrackedApp
}
