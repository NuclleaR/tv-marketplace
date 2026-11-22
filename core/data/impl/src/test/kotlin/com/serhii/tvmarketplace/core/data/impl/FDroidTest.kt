package com.serhii.tvmarketplace.core.data.impl

import com.serhii.tvmarketplace.core.common.TimeSource
import com.serhii.tvmarketplace.core.data.FDroidAppSummary
import com.serhii.tvmarketplace.core.data.LookupResult
import com.serhii.tvmarketplace.core.data.PreviewResult
import com.serhii.tvmarketplace.core.data.ReleaseResult
import com.serhii.tvmarketplace.core.domain.FDroidApp
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class FDroidTest {

  private val now = Instant.parse("2026-11-22T12:00:00Z")
  private val vlc = FDroidApp("org.videolan.vlc")
  private val requests = mutableListOf<String>()

  private fun apk(vararg entries: String): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
      (listOf("AndroidManifest.xml") + entries).forEach {
        zip.putNextEntry(ZipEntry(it))
        zip.write(ByteArray(100) { 1 })
        zip.closeEntry()
      }
    }
    return out.toByteArray()
  }

  private val files = mapOf(
    "/repo/org.videolan.vlc_13070205.apk" to apk("lib/armeabi-v7a/libvlc.so"),
    "/repo/org.videolan.vlc_13070208.apk" to apk("lib/arm64-v8a/libvlc.so"),
    "/repo/org.videolan.vlc_13070308.apk" to apk("lib/arm64-v8a/libvlc.so"),
    "/repo/org.schabi.newpipe_1017.apk" to apk("classes.dex"),
  )

  private val versions =
    """{"packageName":"org.videolan.vlc","suggestedVersionCode":13070208,"packages":[
      {"versionName":"3.7.3-beta","versionCode":13070308},
      {"versionName":"3.7.2","versionCode":13070208},{"versionName":"3.7.2","versionCode":13070205},
      {"versionName":"3.7.1","versionCode":13070108}]}"""

  private fun MockRequestHandleScope.serve(request: HttpRequestData): HttpResponseData {
    val path = request.url.encodedPath
    requests += path
    val file = files[path]
    return when {
      path == "/api/v1/packages/org.videolan.vlc" -> if (request.headers[HttpHeaders.IfNoneMatch] == "\"v1\"") {
        respond("", HttpStatusCode.NotModified)
      } else {
        respond(
          versions,
          HttpStatusCode.OK,
          headersOf(
            HttpHeaders.ContentType to listOf("application/json"),
            HttpHeaders.ETag to listOf("\"v1\""),
          ),
        )
      }
      path == "/api/v1/packages/org.schabi.newpipe" -> respond(
        """{"suggestedVersionCode":1017,"packages":[{"versionName":"0.29.3","versionCode":1017}]}""",
        HttpStatusCode.OK,
        headersOf(HttpHeaders.ContentType, "application/json"),
      )
      path == "/api/search_apps" -> respond(
        """{"apps":[{"name":"NewPipe","summary":"A YouTube frontend","icon":"https://f-droid.org/repo/icon.png",
          "url":"https://f-droid.org/en/packages/org.schabi.newpipe"}]}""",
        HttpStatusCode.OK,
        headersOf(HttpHeaders.ContentType, "application/json"),
      )
      path == "/repo/org.videolan.vlc/en-US/changelogs/13070208.txt" -> respond("Faster <seek>\n\nFixes", HttpStatusCode.OK)
      file != null -> {
        val range = checkNotNull(request.headers[HttpHeaders.Range]).removePrefix("bytes=")
        val (from, to) = if (range.startsWith("-")) {
          maxOf(0, file.size - range.drop(1).toInt()) to file.size - 1
        } else {
          range.substringBefore('-').toInt() to range.substringAfter('-').toInt()
        }
        respond(
          file.copyOfRange(from, to + 1),
          HttpStatusCode.PartialContent,
          headersOf(
            HttpHeaders.ContentRange to listOf("bytes $from-$to/${file.size}"),
            HttpHeaders.LastModified to listOf("Fri, 20 Nov 2026 21:28:36 GMT"),
          ),
        )
      }
      else -> respondError(HttpStatusCode.NotFound)
    }
  }

  private val client = gitHubHttpClient(MockEngine { serve(it) })

  @Test
  fun `the suggested version's builds, each named by the architecture inside it`() = runTest {
    val result =
      assertIs<ReleaseResult.Found>(FDroidReleaseSource(client, TimeSource { now }).latest(vlc, includePrereleases = false, etag = null))
    val release = checkNotNull(result.release)
    assertEquals("3.7.2", release.tag)
    assertEquals(false, release.prerelease)
    assertEquals(Instant.parse("2026-11-20T21:28:36Z"), release.publishedAt)
    assertEquals(
      listOf("org.videolan.vlc_13070208_arm64-v8a.apk", "org.videolan.vlc_13070205_armeabi-v7a.apk"),
      release.packages.map { it.fileName },
    )
    assertEquals("https://f-droid.org/repo/org.videolan.vlc_13070208.apk", release.packages.first().downloadUrl)
    assertEquals(files.getValue("/repo/org.videolan.vlc_13070208.apk").size.toLong(), release.packages.first().sizeBytes)
    assertEquals("<p>Faster &lt;seek&gt;</p><p>Fixes</p>", release.notesHtml)
    assertEquals("\"v1\"", result.etag)
  }

  @Test
  fun `betas only with pre-releases, and an unchanged list isn't read again`() = runTest {
    val source = FDroidReleaseSource(client, TimeSource { now })
    val beta = assertIs<ReleaseResult.Found>(source.latest(vlc, includePrereleases = true, etag = null)).release
    assertEquals("3.7.3-beta", beta?.tag)
    assertEquals(true, beta?.prerelease)
    assertEquals(ReleaseResult.NotModified, source.latest(vlc, includePrereleases = false, etag = "\"v1\""))
    assertEquals(ReleaseResult.SourceGone, source.latest(FDroidApp("gone.app"), includePrereleases = false, etag = null))
  }

  @Test
  fun `a single build keeps its plain name`() = runTest {
    val release = assertIs<ReleaseResult.Found>(
      FDroidReleaseSource(client, TimeSource { now }).latest(FDroidApp("org.schabi.newpipe"), includePrereleases = false, etag = null),
    ).release
    assertEquals(listOf("org.schabi.newpipe_1017.apk"), release?.packages?.map { it.fileName })
    assertNull(release?.notesHtml)
  }

  @Test
  fun `search and preview come from the website's endpoints`() = runTest {
    val catalog = FDroidClient(client)
    val newPipe = FDroidAppSummary("org.schabi.newpipe", "NewPipe", "A YouTube frontend", "https://f-droid.org/repo/icon.png")
    assertEquals(LookupResult.Found(listOf(newPipe)), catalog.search("newpipe"))
    val preview = assertIs<PreviewResult.Found>(catalog.preview(FDroidApp("org.schabi.newpipe"))).preview
    assertEquals("NewPipe", preview.displayName)
    assertEquals("0.29.3", preview.latestVersion)
    assertEquals("https://f-droid.org/repo/icon.png", preview.iconUrl)
    assertEquals(PreviewResult.NotFound, catalog.preview(FDroidApp("gone.app")))
  }
}
