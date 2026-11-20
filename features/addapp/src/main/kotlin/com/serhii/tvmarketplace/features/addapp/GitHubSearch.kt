package com.serhii.tvmarketplace.features.addapp

import com.serhii.tvmarketplace.core.data.GitHubRepoSummary
import com.serhii.tvmarketplace.core.data.GitHubRepository
import com.serhii.tvmarketplace.core.data.LookupResult
import kotlinx.coroutines.delay

// a pause in typing before a search goes out: GitHub allows 10 searches a minute without a token
internal const val SEARCH_DEBOUNCE_MS = 400L
internal const val MIN_OWNER_LENGTH = 2

// what the typed text asks for: "nucl" looks for owners, "nucllear/" lists their repositories, "nucllear/tv" filters them
internal sealed interface SearchQuery {
  data object Empty : SearchQuery
  data class Owners(val text: String) : SearchQuery
  data class Repos(
    val owner: String,
    val filter: String,
  ) : SearchQuery

  companion object {
    fun parse(query: String): SearchQuery {
      val text = query.trim()
      if (text.isEmpty()) return Empty
      val slash = text.indexOf('/')
      if (slash < 0) return Owners(text)
      val owner = text.substring(0, slash)
      if (owner.isEmpty()) return Owners(text.trimStart('/'))
      return Repos(owner = owner, filter = text.substring(slash + 1).substringBefore('/'))
    }
  }
}

// names starting with the filter first, then the ones containing it; within each, GitHub's order (recently pushed)
internal fun List<GitHubRepoSummary>.matching(filter: String): List<GitHubRepoSummary> {
  if (filter.isEmpty()) return this
  val (starts, contains) = filter { it.name.contains(filter, ignoreCase = true) }
    .partition { it.name.startsWith(filter, ignoreCase = true) }
  return starts + contains
}

internal class GitHubSearch(private val gitHub: GitHubRepository) {

  // called on every change of the text; cancelled by the next one, which is what makes the pause a debounce
  suspend fun search(query: SearchQuery): SearchResults = when (query) {
    SearchQuery.Empty -> SearchResults.None
    is SearchQuery.Owners -> owners(query.text)
    is SearchQuery.Repos -> repos(query.owner, query.filter)
  }

  private suspend fun owners(text: String): SearchResults {
    if (text.length < MIN_OWNER_LENGTH) return SearchResults.KeepTyping
    delay(SEARCH_DEBOUNCE_MS)
    return when (val result = gitHub.searchOwners(text)) {
      is LookupResult.Found -> if (result.value.isEmpty()) {
        SearchResults.NothingFound
      } else {
        SearchResults.Owners(result.value.map { SearchResults.Owner(it.login, it.avatarUrl, it.organization) })
      }
      LookupResult.NotFound -> SearchResults.NothingFound
      else -> result.failure(owner = text)
    }
  }

  private suspend fun repos(
    owner: String,
    filter: String,
  ): SearchResults {
    // the owner's list is fetched once and cached; filtering it costs nothing, so only a new owner waits for a pause
    val listed = when (val result = gitHub.ownerRepos(owner)) {
      is LookupResult.Found -> result.value
      else -> return result.failure(owner)
    }
    val local = listed.repos.matching(filter)
    val repos = if (local.isEmpty() && !listed.complete && filter.isNotEmpty()) {
      delay(SEARCH_DEBOUNCE_MS)
      when (val result = gitHub.searchOwnerRepos(listed.owner, filter)) {
        is LookupResult.Found -> result.value.matching(filter)
        else -> return result.failure(owner)
      }
    } else {
      local
    }
    if (repos.isEmpty()) return SearchResults.NothingFound
    return SearchResults.Repos(
      owner = listed.owner,
      repos = repos.map { SearchResults.Repo(owner = it.owner, name = it.name, description = it.description) },
      partial = !listed.complete && filter.isEmpty(),
    )
  }

  private fun LookupResult<*>.failure(owner: String): SearchResults = when (this) {
    LookupResult.NotFound -> SearchResults.OwnerNotFound(owner)
    LookupResult.RateLimited -> SearchResults.RateLimited
    else -> SearchResults.NetworkError
  }
}
