package com.serhii.tvmarketplace.features.addapp.preview

sealed interface PreviewViewState {
  data object Loading : PreviewViewState

  data class Content(
    val name: String,
    val ownerAndRepo: String,
    val description: String?,
    val latestVersion: String?,
    val avatarUrl: String?,
    val sender: String?,
    val alreadyTracked: Boolean,
  ) : PreviewViewState

  data class Error(val kind: ErrorKind) : PreviewViewState

  data class Added(val name: String) : PreviewViewState

  enum class ErrorKind { NotFound, RateLimited, Network, LinkMissing }
}
