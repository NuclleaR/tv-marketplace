package com.serhii.tvmarketplace.core.data.impl

import com.serhii.tvmarketplace.core.common.TimeSource
import com.serhii.tvmarketplace.core.data.PreviewResult
import com.serhii.tvmarketplace.core.domain.AppPreview
import com.serhii.tvmarketplace.core.domain.GitHubRepoRef
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class KtorGitHubRepositoryTest {

  private val ref = GitHubRepoRef(owner = "ImranR98", repo = "Obtainium")
  private var now = Instant.parse("2026-11-18T12:00:00Z")
  private val requests = mutableListOf<String>()

  private fun repository(handler: suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData) =
    KtorGitHubRepository(
      client = gitHubHttpClient(
        MockEngine { request ->
          requests += request.url.encodedPath
          handler(request)
        },
      ),
      timeSource = TimeSource { now },
    )

  private fun fixture(name: String) = checkNotNull(javaClass.getResource("/github/$name")).readText()

  private fun MockRequestHandleScope.json(body: String) =
    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

  @Test
  fun `builds a preview from the repository and its newest published release`() = runTest {
    val repository = repository { request ->
      if (request.url.encodedPath.endsWith("/releases")) json(fixture("releases.json")) else json(fixture("repo.json"))
    }
    assertEquals(
      PreviewResult.Found(
        AppPreview(
          ref = ref,
          displayName = "Obtainium",
          description = "Get Android app updates straight from the source.",
          latestVersion = "v1.2.3",
          avatarUrl = "https://avatars.githubusercontent.com/u/1?v=4",
        ),
      ),
      repository.preview(ref),
    )
    assertEquals(listOf("/repos/ImranR98/Obtainium", "/repos/ImranR98/Obtainium/releases"), requests)
  }

  @Test
  fun `a repository without releases has no latest version`() = runTest {
    val repository = repository { request ->
      if (request.url.encodedPath.endsWith("/releases")) json("[]") else json(fixture("repo.json"))
    }
    assertEquals(null, (repository.preview(ref) as PreviewResult.Found).preview.latestVersion)
  }

  @Test
  fun `a pre-release counts as the latest version`() = runTest {
    val repository = repository { request ->
      if (request.url.encodedPath.endsWith("/releases")) json(fixture("releases-prerelease.json")) else json(fixture("repo.json"))
    }
    assertEquals("v0.1.0-rc1", (repository.preview(ref) as PreviewResult.Found).preview.latestVersion)
  }

  @Test
  fun `maps a missing repository, the rate limit and network errors`() = runTest {
    assertEquals(PreviewResult.NotFound, repository { respondError(HttpStatusCode.NotFound) }.preview(ref))
    assertEquals(
      PreviewResult.RateLimited,
      repository { respond("", HttpStatusCode.Forbidden, headersOf("x-ratelimit-remaining", "0")) }.preview(ref),
    )
    assertEquals(PreviewResult.NetworkError, repository { throw IOException("offline") }.preview(ref))
  }

  @Test
  fun `caches a preview for ten minutes`() = runTest {
    val repository = repository { request ->
      if (request.url.encodedPath.endsWith("/releases")) json("[]") else json(fixture("repo.json"))
    }
    val _ = repository.preview(ref)
    now += Duration.ofMinutes(9)
    val _ = repository.preview(ref)
    assertEquals(2, requests.size)
    now += Duration.ofMinutes(2)
    val _ = repository.preview(ref)
    assertEquals(4, requests.size)
  }
}
