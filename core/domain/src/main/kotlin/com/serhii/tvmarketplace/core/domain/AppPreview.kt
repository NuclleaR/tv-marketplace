package com.serhii.tvmarketplace.core.domain

data class AppPreview(
  val ref: AppSource,
  val displayName: String,
  val description: String?,
  val latestVersion: String?,
  // the GitHub owner's picture, never shown as the app's icon
  val avatarUrl: String?,
  // the app's own icon: from the repository's metadata or sources, or F-Droid's
  val iconUrl: String? = null,
)
