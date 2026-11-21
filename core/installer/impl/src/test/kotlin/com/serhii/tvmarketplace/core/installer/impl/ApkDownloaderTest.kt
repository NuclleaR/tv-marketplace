package com.serhii.tvmarketplace.core.installer.impl

import com.serhii.tvmarketplace.core.domain.AppPackage
import com.serhii.tvmarketplace.core.domain.FailureReason
import com.serhii.tvmarketplace.core.installer.ApkInfo
import com.serhii.tvmarketplace.core.installer.PackageInspector
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ApkDownloaderTest {

  @get:Rule
  val folder = TemporaryFolder()

  private val body = ByteArray(300_000) { (it % 251).toByte() }
  private val sha256 = MessageDigest.getInstance("SHA-256").digest(body).joinToString("") { "%02x".format(it) }
  private val apk = ApkInfo("com.example.app", "1.3.0", 130)
  private val inspector = mockk<PackageInspector> { every { inspect(any()) } returns apk }
  private var freeBytes = Long.MAX_VALUE
  private val progress = mutableListOf<Long>()

  private fun pkg(
    size: Long = body.size.toLong(),
    sha: String? = sha256,
    url: String = "https://github.com/o/r/releases/download/v1/app.apk",
  ) = AppPackage(7, "app.apk", size, url, sha)

  private fun downloader(engine: MockEngine) = ApkDownloader(HttpClient(engine), inspector, folder.root, freeBytes = { freeBytes })

  private val serving = MockEngine { request ->
    when (request.url.host) {
      "github.com" -> respond(
        "",
        HttpStatusCode.Found,
        headersOf(HttpHeaders.Location, "https://release-assets.githubusercontent.com/a.apk"),
      )
      "release-assets.githubusercontent.com" -> respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, body.size.toString()))
      else -> respond(body, HttpStatusCode.OK)
    }
  }

  @Test
  fun `downloads through GitHub's redirect, reports progress and checks the file`() = runTest {
    val result = downloader(serving).download(appId = 1, pkg = pkg(), expectedPackage = "com.example.app") { bytes, _ -> progress += bytes }
    assertIs<ApkDownloader.Outcome.Done>(result)
    assertEquals(apk, result.apk)
    assertEquals(body.toList(), result.file.readBytes().toList())
    assertEquals("1-7.apk", result.file.name)
    assertEquals(body.size.toLong(), progress.last())
    // a few updates, not one per buffer
    assertTrue(progress.size in 2..101)
    assertEquals(listOf("1-7.apk"), folder.root.list()?.toList())
  }

  @Test
  fun `a wrong size or checksum is a damaged download, and leaves nothing behind`() = runTest {
    assertEquals(ApkDownloader.Outcome.Failed(FailureReason.Corrupt), downloader(serving).download(1, pkg(size = 5), null) { _, _ -> })
    assertEquals(ApkDownloader.Outcome.Failed(FailureReason.Corrupt), downloader(serving).download(1, pkg(sha = "00"), null) { _, _ -> })
    assertEquals(emptyList(), folder.root.list()?.toList())
  }

  @Test
  fun `a file that isn't an APK, or is another app, is refused`() = runTest {
    every { inspector.inspect(any()) } returns null
    assertEquals(ApkDownloader.Outcome.Failed(FailureReason.Corrupt), downloader(serving).download(1, pkg(), null) { _, _ -> })
    every { inspector.inspect(any()) } returns apk
    assertEquals(ApkDownloader.Outcome.Failed(FailureReason.DifferentApp), downloader(serving).download(1, pkg(), "com.other") { _, _ -> })
    assertEquals(emptyList(), folder.root.list()?.toList())
  }

  @Test
  fun `not enough space doesn't start`() = runTest {
    freeBytes = 100_000
    val result = downloader(serving).download(1, pkg(), null) { _, _ -> }
    assertEquals(ApkDownloader.Outcome.Failed(FailureReason.NoSpace(330_000)), result)
  }

  @Test
  fun `only GitHub's own hosts are downloaded from`() = runTest {
    val result = downloader(serving).download(1, pkg(url = "https://example.com/app.apk"), null) { _, _ -> }
    assertEquals(ApkDownloader.Outcome.Failed(FailureReason.Corrupt), result)
    val redirected = MockEngine { request ->
      if (request.url.host == "github.com") {
        respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://evil.example/a.apk"))
      } else {
        respond(body, HttpStatusCode.OK)
      }
    }
    assertEquals(ApkDownloader.Outcome.Failed(FailureReason.Corrupt), downloader(redirected).download(1, pkg(), null) { _, _ -> })
  }

  @Test
  fun `a dropped connection is a network failure`() = runTest {
    val result = downloader(MockEngine { throw IOException("reset") }).download(1, pkg(), null) { _, _ -> }
    assertEquals(ApkDownloader.Outcome.Failed(FailureReason.Network), result)
    assertEquals(
      ApkDownloader.Outcome.Failed(FailureReason.Network),
      downloader(MockEngine { respond("", HttpStatusCode.BadGateway) }).download(1, pkg(), null) { _, _ -> },
    )
    assertEquals(emptyList(), folder.root.list()?.toList())
  }
}
