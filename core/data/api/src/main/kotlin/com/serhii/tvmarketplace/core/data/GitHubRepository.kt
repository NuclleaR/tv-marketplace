package com.serhii.tvmarketplace.core.data

import com.serhii.tvmarketplace.core.domain.AppPreview
import com.serhii.tvmarketplace.core.domain.GitHubRepoRef

sealed interface PreviewResult {
  data class Found(val preview: AppPreview) : PreviewResult
  data object NotFound : PreviewResult
  data object RateLimited : PreviewResult
  data object NetworkError : PreviewResult
}

interface GitHubRepository {
  suspend fun preview(ref: GitHubRepoRef): PreviewResult
}
