package com.serhii.tvmarketplace.features.addapp

import com.serhii.tvmarketplace.core.data.GitHubRepoSummary
import com.serhii.tvmarketplace.core.data.GitHubRepository
import com.serhii.tvmarketplace.core.data.LookupResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

// a pause in typing before a search goes out: GitHub allows 10 searches a minute without a token
internal const val SEARCH_DEBOUNCE_MS = 400L
internal const val MIN_NAME_LENGTH = 2

// what the typed text asks for: "jelly" looks for repositories named like it and for owners, "nucllear/" lists that
// owner's repositories, "nucllear/tv" filters them
internal sealed interface SearchQuery {
  data object Empty : SearchQuery
  data class Name(val text: String) : SearchQuery
  data class Repos(
    val owner: String,
    val filter: String,
  ) : SearchQuery

  companion object {
    fun parse(query: String): SearchQuery {
      val text = query.trim()
      if (text.isEmpty()) return Empty
      val slash = text.indexOf('/')
      if (slash < 0) return Name(text)
      val owner = text.substring(0, slash)
      if (owner.isEmpty()) return Name(text.trimStart('/'))
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
    is SearchQuery.Name -> name(query.text)
    is SearchQuery.Repos -> repos(query.owner, query.filter)
  }

  // most people know an app by its name, not by who publishes it: repositories first, owners after them
  private suspend fun name(text: String): SearchResults = coroutineScope {
    if (text.length < MIN_NAME_LENGTH) return@coroutineScope SearchResults.KeepTyping
    delay(SEARCH_DEBOUNCE_MS)
    val reposAsync = async { gitHub.searchRepos(text) }
    val ownersAsync = async { gitHub.searchOwners(text) }
    val repos = when (val result = reposAsync.await()) {
      // GitHub ranks by relevance and popularity; only a repository named exactly like the text goes before that
      is LookupResult.Found -> result.value.sortedByDescending { it.name.equals(text, ignoreCase = true) }
      LookupResult.NotFound -> emptyList()
      else -> return@coroutineScope result.failure(owner = text)
    }
    // owners only add to the repositories: without them the search still answers
    val owners = (ownersAsync.await() as? LookupResult.Found)?.value.orEmpty()
    if (repos.isEmpty() && owners.isEmpty()) return@coroutineScope SearchResults.NothingFound
    SearchResults.Matches(
      repos = repos.map { SearchResults.Repo(owner = it.owner, name = it.name, description = it.description) },
      owners = owners.map { SearchResults.Owner(it.login, it.avatarUrl, it.organization) },
    )
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
