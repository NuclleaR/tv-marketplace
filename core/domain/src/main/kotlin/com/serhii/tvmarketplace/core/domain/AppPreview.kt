package com.serhii.tvmarketplace.core.domain

data class AppPreview(
  val ref: GitHubRepoRef,
  val displayName: String,
  val description: String?,
  val latestVersion: String?,
  val avatarUrl: String?,
  // the app's own icon from the repository's fastlane metadata, when it has one
  val iconUrl: String? = null,
)
