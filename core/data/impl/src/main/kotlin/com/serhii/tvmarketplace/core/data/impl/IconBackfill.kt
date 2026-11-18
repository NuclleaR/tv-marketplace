package com.serhii.tvmarketplace.core.data.impl

import com.serhii.tvmarketplace.core.data.GitHubRepository
import com.serhii.tvmarketplace.core.data.PreviewResult
import com.serhii.tvmarketplace.core.data.TrackedAppsRepository
import dev.zacsweers.metro.Inject

// apps tracked before icons were looked up get theirs once; run at startup, it costs nothing when all are checked
@Inject
class IconBackfill(
  private val trackedApps: TrackedAppsRepository,
  private val gitHub: GitHubRepository,
) {

  suspend fun run() {
    for (app in trackedApps.withUncheckedIcon()) {
      when (val result = gitHub.preview(app.ref)) {
        is PreviewResult.Found -> trackedApps.setIcon(app.id, result.preview.iconUrl)
        // a repository that is gone has no icon to find
        PreviewResult.NotFound -> trackedApps.setIcon(app.id, null)
        // GitHub is limiting requests or unreachable: the rest waits for the next start
        PreviewResult.RateLimited, PreviewResult.NetworkError -> return
      }
    }
  }
}
