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
import io.ktor.http.ContentType
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

  private fun MockRequestHandleScope.gitHub(
    request: HttpRequestData,
    releases: String = "[]",
    rawFiles: Map<String, Pair<String, ContentType>> = emptyMap(),
  ) = when {
    request.url.host == "raw.githubusercontent.com" -> rawFiles[request.url.encodedPath.removePrefix("/ImranR98/Obtainium/main/")]
      ?.let { (body, type) -> respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, type.toString())) }
      ?: respondError(HttpStatusCode.NotFound)
    request.url.encodedPath.endsWith("/releases") -> json(releases)
    else -> json(fixture("repo.json"))
  }

  @Test
  fun `builds a preview from the repository and its newest published release`() = runTest {
    val repository = repository { request -> gitHub(request, releases = fixture("releases.json")) }
    assertEquals(
      PreviewResult.Found(
        AppPreview(
          ref = ref,
          displayName = "Obtainium",
          description = "Get Android app updates straight from the source.",
          latestVersion = "v1.2.3",
          avatarUrl = "https://avatars.githubusercontent.com/u/1?v=4",
          iconUrl = null,
        ),
      ),
      repository.preview(ref),
    )
    assertEquals(
      listOf(
        "/repos/ImranR98/Obtainium",
        "/repos/ImranR98/Obtainium/releases",
        "/ImranR98/Obtainium/main/fastlane/metadata/android/en-US/images/icon.png",
      ),
      requests,
    )
  }

  private val iconPath = "fastlane/metadata/android/en-US/images/icon.png"
  private val png = "" to ContentType.Image.PNG

  private suspend fun iconUrl(rawFiles: Map<String, Pair<String, ContentType>>) =
    (repository { request -> gitHub(request, rawFiles = rawFiles) }.preview(ref) as PreviewResult.Found).preview.iconUrl

  @Test
  fun `uses the fastlane app icon when the repository has one`() = runTest {
    assertEquals("https://raw.githubusercontent.com/ImranR98/Obtainium/main/$iconPath", iconUrl(mapOf(iconPath to png)))
  }

  @Test
  fun `follows a fastlane icon that is a git symlink`() = runTest {
    // raw.githubusercontent.com serves a symlink as text holding its target path
    val link = "../../../../../assets/graphics/icon-512x512.png" to ContentType.Text.Plain
    assertEquals(
      "https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/icon-512x512.png",
      iconUrl(mapOf(iconPath to link, "assets/graphics/icon-512x512.png" to png)),
    )
  }

  @Test
  fun `a fastlane icon that is not an image is ignored`() = runTest {
    assertEquals(null, iconUrl(mapOf(iconPath to ("version https://git-lfs.github.com/spec/v1" to ContentType.Text.Plain))))
    assertEquals(null, iconUrl(mapOf(iconPath to ("../missing.png" to ContentType.Text.Plain))))
    assertEquals(null, iconUrl(mapOf(iconPath to ("../../../../../../../x.png" to ContentType.Text.Plain), "x.png" to png)))
  }

  @Test
  fun `a repository without releases has no latest version`() = runTest {
    val repository = repository { request -> gitHub(request) }
    assertEquals(null, (repository.preview(ref) as PreviewResult.Found).preview.latestVersion)
  }

  @Test
  fun `a pre-release counts as the latest version`() = runTest {
    val repository = repository { request -> gitHub(request, releases = fixture("releases-prerelease.json")) }
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
    val repository = repository { request -> gitHub(request) }
    val _ = repository.preview(ref)
    now += Duration.ofMinutes(9)
    val _ = repository.preview(ref)
    assertEquals(3, requests.size)
    now += Duration.ofMinutes(2)
    val _ = repository.preview(ref)
    assertEquals(6, requests.size)
  }
}
