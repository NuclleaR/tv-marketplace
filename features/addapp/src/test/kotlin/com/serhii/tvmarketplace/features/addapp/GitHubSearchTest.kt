package com.serhii.tvmarketplace.features.addapp

import com.serhii.tvmarketplace.core.testing.repoSummary
import kotlin.test.Test
import kotlin.test.assertEquals

class GitHubSearchTest {

  @Test
  fun `text without a slash looks for owners, with one for an owner's repositories`() {
    assertEquals(SearchQuery.Empty, SearchQuery.parse("  "))
    assertEquals(SearchQuery.Owners("nucl"), SearchQuery.parse("nucl"))
    assertEquals(SearchQuery.Repos("nucllear", ""), SearchQuery.parse("nucllear/"))
    assertEquals(SearchQuery.Repos("nucllear", "tv"), SearchQuery.parse("nucllear/tv"))
    // a path after the repository is ignored, a leading slash too
    assertEquals(SearchQuery.Repos("nucllear", "tv"), SearchQuery.parse("nucllear/tv/releases"))
    assertEquals(SearchQuery.Owners("nucl"), SearchQuery.parse("/nucl"))
  }

  @Test
  fun `names starting with the filter come first, then those containing it`() {
    val repos = listOf("android-template", "tv-marketplace", "Snake", "test_apps").map { repoSummary("NuclleaR", it) }
    assertEquals(listOf("tv-marketplace", "test_apps", "android-template"), repos.matching("t").map { it.name })
    assertEquals(listOf("Snake"), repos.matching("SNA").map { it.name })
    assertEquals(repos, repos.matching(""))
  }
}
