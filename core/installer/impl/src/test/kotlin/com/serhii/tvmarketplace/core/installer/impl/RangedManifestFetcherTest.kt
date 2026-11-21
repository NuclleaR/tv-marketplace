package com.serhii.tvmarketplace.core.installer.impl

import com.serhii.tvmarketplace.core.domain.AppPackage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RangedManifestFetcherTest {

  private val apk = checkNotNull(javaClass.getResource("/apk/padded-deflated.apk")).readBytes()
  private val requests = mutableListOf<HttpRequestData>()
  private val pkg = AppPackage(1, "app.apk", apk.size.toLong(), "https://github.com/o/r/releases/download/v1/app.apk")

  private fun server(honorRange: Boolean = true) = MockEngine { request ->
    requests += request
    when (request.url.host) {
      "github.com" -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://release-assets.githubusercontent.com/x"))
      else -> {
        val range = request.headers[HttpHeaders.Range]
        if (!honorRange || range == null) {
          respond(apk, HttpStatusCode.OK)
        } else {
          val (from, to) = range.removePrefix("bytes=").split("-").map { it.toInt() }
          respond(apk.copyOfRange(from, to + 1), HttpStatusCode.PartialContent)
        }
      }
    }
  }

  @Test
  fun `reads the package name through ranged requests without fetching the whole file`() = runTest {
    assertEquals("org.example.another.app", RangedManifestFetcher(HttpClient(server())).learn(pkg))
    val assetRequests = requests.filter { it.url.host != "github.com" }
    // the tail holds the central directory of a small file, so one more request for the manifest
    assertTrue(assetRequests.size <= 3)
    assertTrue(assetRequests.all { it.headers[HttpHeaders.Range] != null })
  }

  @Test
  fun `a server that ignores ranges gets nothing read`() = runTest {
    assertNull(RangedManifestFetcher(HttpClient(server(honorRange = false))).learn(pkg))
    assertEquals(2, requests.size)
  }

  @Test
  fun `other hosts are never asked`() = runTest {
    assertNull(RangedManifestFetcher(HttpClient(server())).learn(pkg.copy(downloadUrl = "https://example.com/app.apk")))
    assertEquals(0, requests.size)
  }
}
