package com.serhii.tvmarketplace.core.data.impl

import com.serhii.tvmarketplace.core.data.ReleaseResult
import com.serhii.tvmarketplace.core.domain.AppPackage
import com.serhii.tvmarketplace.core.domain.AppSource
import com.serhii.tvmarketplace.core.domain.GitHubRepoRef
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class GitHubReleaseSourceTest {

  private val source = GitHubRepoRef("yuliskov", "SmartTube")
  private val requests = mutableListOf<HttpRequestData>()
  private val now = Instant.parse("2026-11-20T12:00:00Z")

  private fun fixture(name: String) = checkNotNull(javaClass.getResource("/github/releases/$name")).readText()

  private fun releaseSource(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) = GitHubReleaseSource(
    gitHubHttpClient(
      MockEngine { request ->
        requests += request
        handler(request)
      },
    ),
    timeSource = { now },
  )

  private fun MockRequestHandleScope.json(
    body: String,
    vararg headers: Pair<String, String>,
  ) = respond(
    body,
    HttpStatusCode.OK,
    headersOf(
      *(
        headers.map {
          it.first to listOf(it.second)
        } + (HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()))
        ).toTypedArray(),
    ),
  )

  @Test
  fun `asks for the releases with their notes rendered, and the etag it has`() = runTest {
    val _ = releaseSource { json(fixture("releases.json")) }.latest(source, includePrereleases = false, etag = "\"abc\"")
    val request = requests.single()
    assertEquals("/repos/yuliskov/SmartTube/releases", request.url.encodedPath)
    assertEquals("10", request.url.parameters["per_page"])
    assertEquals("application/vnd.github.html+json", request.headers[HttpHeaders.Accept])
    assertEquals("\"abc\"", request.headers[HttpHeaders.IfNoneMatch])
  }

  @Test
  fun `the latest release skips drafts and pre-releases, and keeps only packages`() = runTest {
    val result = releaseSource { json(fixture("releases.json"), HttpHeaders.ETag to "\"v2\"") }.latest(source, false, null)
    assertIs<ReleaseResult.Found>(result)
    assertEquals("\"v2\"", result.etag)
    val release = checkNotNull(result.release)
    assertEquals("v1.3.0", release.tag)
    assertEquals("1.3.0", release.name)
    assertEquals(Instant.parse("2026-11-10T10:00:00Z"), release.publishedAt)
    assertEquals(false, release.prerelease)
    assertEquals(
      listOf(
        AppPackage(
          21,
          "SmartTube_stable_1.3.0_armeabi-v7a.apk",
          18_000_000,
          "https://github.com/yuliskov/SmartTube/releases/download/v1.3.0/SmartTube_stable_1.3.0_armeabi-v7a.apk",
          sha256 = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
        ),
        AppPackage(
          22,
          "SmartTube_stable_1.3.0_arm64-v8a.apk",
          19_000_000,
          "https://github.com/yuliskov/SmartTube/releases/download/v1.3.0/SmartTube_stable_1.3.0_arm64-v8a.apk",
        ),
      ),
      release.packages,
    )
    assertEquals(true, release.notesHtml?.contains("What's new"))
  }

  @Test
  fun `pre-releases count for apps that opted in`() = runTest {
    val result = releaseSource { json(fixture("releases.json")) }.latest(source, includePrereleases = true, etag = null)
    assertEquals("v2.0.0-beta1", (result as ReleaseResult.Found).release?.tag)
  }

  @Test
  fun `no releases is found with nothing in it`() = runTest {
    val result = releaseSource { json(fixture("empty.json")) }.latest(source, false, null)
    assertNull((result as ReleaseResult.Found).release)
  }

  @Test
  fun `not modified, gone, limited and broken answers`() = runTest {
    assertEquals(ReleaseResult.NotModified, releaseSource { respond("", HttpStatusCode.NotModified) }.latest(source, false, "\"x\""))
    assertEquals(ReleaseResult.SourceGone, releaseSource { respond("", HttpStatusCode.NotFound) }.latest(source, false, null))
    assertEquals(
      ReleaseResult.RateLimited(Instant.ofEpochSecond(1_800_000_000)),
      releaseSource {
        respond(
          "",
          HttpStatusCode.Forbidden,
          headersOf("x-ratelimit-remaining" to listOf("0"), "x-ratelimit-reset" to listOf("1800000000")),
        )
      }.latest(source, false, null),
    )
    // a secondary limit says how long to wait instead
    assertEquals(
      ReleaseResult.RateLimited(now.plusSeconds(120)),
      releaseSource { respond("", HttpStatusCode.TooManyRequests, headersOf("retry-after", "120")) }.latest(source, false, null),
    )
    assertEquals(
      ReleaseResult.RateLimited(now.plusSeconds(60)),
      releaseSource {
        respond("", HttpStatusCode.Forbidden)
      }.latest(source, false, null),
    )
    assertEquals(ReleaseResult.NetworkError, releaseSource { respond("", HttpStatusCode.BadGateway) }.latest(source, false, null))
    assertEquals(ReleaseResult.NetworkError, releaseSource { json("{not json") }.latest(source, false, null))
    assertEquals(ReleaseResult.NetworkError, releaseSource { throw IOException("offline") }.latest(source, false, null))
  }
}
