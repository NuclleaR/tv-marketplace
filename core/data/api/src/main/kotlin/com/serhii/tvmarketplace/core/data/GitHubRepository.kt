package com.serhii.tvmarketplace.core.data

import com.serhii.tvmarketplace.core.domain.AppPreview
import com.serhii.tvmarketplace.core.domain.GitHubRepoRef

sealed interface PreviewResult {
  data class Found(val preview: AppPreview) : PreviewResult
  data object NotFound : PreviewResult
  data object RateLimited : PreviewResult
  data object NetworkError : PreviewResult
}

sealed interface LookupResult<out T> {
  data class Found<T>(val value: T) : LookupResult<T>
  data object NotFound : LookupResult<Nothing>
  data object RateLimited : LookupResult<Nothing>
  data object NetworkError : LookupResult<Nothing>
}

data class GitHubOwner(
  val login: String,
  val avatarUrl: String?,
  val organization: Boolean,
)

data class GitHubRepoSummary(
  val owner: String,
  val name: String,
  val description: String?,
  val fork: Boolean,
  val archived: Boolean,
  val stars: Int,
)

// the owner's most recently pushed repositories; complete is false when the owner has more than were fetched
data class OwnerRepos(
  val owner: String,
  val repos: List<GitHubRepoSummary>,
  val complete: Boolean,
)

interface GitHubRepository {
  suspend fun preview(ref: GitHubRepoRef): PreviewResult

  // users and organizations whose login matches, best match first
  suspend fun searchOwners(query: String): LookupResult<List<GitHubOwner>>

  // NotFound when there is no such user or organization
  suspend fun ownerRepos(owner: String): LookupResult<OwnerRepos>

  // for owners with more repositories than ownerRepos returns: their repositories whose name matches
  suspend fun searchOwnerRepos(
    owner: String,
    name: String,
  ): LookupResult<List<GitHubRepoSummary>>
}
