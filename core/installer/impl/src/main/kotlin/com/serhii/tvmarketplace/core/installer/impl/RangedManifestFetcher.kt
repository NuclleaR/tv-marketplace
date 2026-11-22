package com.serhii.tvmarketplace.core.installer.impl

import com.serhii.tvmarketplace.core.common.AppLog
import com.serhii.tvmarketplace.core.data.PackageIdentity
import com.serhii.tvmarketplace.core.domain.ApkManifestReader
import com.serhii.tvmarketplace.core.domain.AppPackage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.takeFrom
import kotlinx.coroutines.CancellationException
import java.io.IOException

private val MANIFEST_HOSTS = setOf("github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")
private const val MAX_REDIRECTS = 5

// reads a package's name from three ranged requests on its download, a few KB instead of the whole APK; a server
// that ignores Range gets nothing read from it
@ContributesBinding(AppScope::class)
@Inject
class RangedManifestFetcher(client: HttpClient) : PackageIdentity {

  private val client = client.config {
    followRedirects = false
    expectSuccess = false
  }

  override suspend fun learn(pkg: AppPackage): String? = try {
    read(pkg).also { AppLog.event("manifest_read", "found" to if (it != null) 1 else 0) }
  } catch (e: CancellationException) {
    throw e
  } catch (e: IOException) {
    AppLog.error("manifest_read_failed", e)
    throw e
  }

  private suspend fun read(pkg: AppPackage): String? {
    val size = pkg.sizeBytes
    if (size <= 0) return null
    var url = Url(pkg.downloadUrl)
    val tailStart = maxOf(0, size - ApkManifestReader.TAIL_SIZE)
    val (resolved, tail) = range(url, tailStart, size - 1) ?: return null
    url = resolved
    val directory = ApkManifestReader.centralDirectory(tail, size) ?: return null
    // the directory is usually inside the tail already
    val directoryBytes = if (directory.offset >= tailStart) {
      val from = (directory.offset - tailStart).toInt()
      tail.copyOfRange(from, from + directory.size.toInt())
    } else {
      range(url, directory.offset, directory.offset + directory.size - 1)?.second ?: return null
    }
    val entry = ApkManifestReader.manifestEntry(directoryBytes) ?: return null
    val end = minOf(size - 1, entry.localHeaderOffset + entry.fetchSize - 1)
    val local = range(url, entry.localHeaderOffset, end)?.second ?: return null
    val manifest = ApkManifestReader.manifest(local, entry) ?: return null
    return ApkManifestReader.packageName(manifest)
  }

  // the bytes from..to of the file, and the URL that served them after redirects; null without a 206
  private suspend fun range(
    start: Url,
    from: Long,
    to: Long,
  ): Pair<Url, ByteArray>? {
    var url = start
    repeat(MAX_REDIRECTS) {
      if (url.protocol.name != "https" || url.host !in MANIFEST_HOSTS) return null
      val next: Any? = client.prepareGet(url) { header(HttpHeaders.Range, "bytes=$from-$to") }.execute { response ->
        when {
          response.status.value in 300..399 -> response.headers[HttpHeaders.Location]?.let { URLBuilder(url).takeFrom(it).build() }
          // a 200 would be the whole file: leave without reading it
          response.status != HttpStatusCode.PartialContent -> null
          else -> response.readRawBytes().takeIf { it.size.toLong() == to - from + 1 }?.let { url to it }
        }
      }
      when (next) {
        is Url -> url = next
        is Pair<*, *> -> {
          @Suppress("UNCHECKED_CAST")
          return next as Pair<Url, ByteArray>
        }
        else -> return null
      }
    }
    return null
  }
}
