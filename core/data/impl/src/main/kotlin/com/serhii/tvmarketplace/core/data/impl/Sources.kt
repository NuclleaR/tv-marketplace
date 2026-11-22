package com.serhii.tvmarketplace.core.data.impl

import com.serhii.tvmarketplace.core.data.AppPreviews
import com.serhii.tvmarketplace.core.data.FDroidCatalog
import com.serhii.tvmarketplace.core.data.GitHubRepository
import com.serhii.tvmarketplace.core.data.PreviewResult
import com.serhii.tvmarketplace.core.data.ReleaseResult
import com.serhii.tvmarketplace.core.data.ReleaseSource
import com.serhii.tvmarketplace.core.domain.AppSource
import com.serhii.tvmarketplace.core.domain.FDroidApp
import com.serhii.tvmarketplace.core.domain.GitHubRepoRef
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

// asks each app's own source
@ContributesBinding(AppScope::class)
@Inject
class SourceReleases(
  private val gitHub: GitHubReleaseSource,
  private val fDroid: FDroidReleaseSource,
) : ReleaseSource {

  override suspend fun latest(
    source: AppSource,
    includePrereleases: Boolean,
    etag: String?,
  ): ReleaseResult = when (source) {
    is GitHubRepoRef -> gitHub.latest(source, includePrereleases, etag)
    is FDroidApp -> fDroid.latest(source, includePrereleases, etag)
  }
}

@ContributesBinding(AppScope::class)
@Inject
class SourcePreviews(
  private val gitHub: GitHubRepository,
  private val fDroid: FDroidCatalog,
) : AppPreviews {

  override suspend fun preview(source: AppSource): PreviewResult = when (source) {
    is GitHubRepoRef -> gitHub.preview(source)
    is FDroidApp -> fDroid.preview(source)
  }
}
