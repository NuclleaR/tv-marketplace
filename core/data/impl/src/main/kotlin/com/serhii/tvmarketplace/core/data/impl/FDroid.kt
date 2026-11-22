package com.serhii.tvmarketplace.core.data.impl

import com.serhii.tvmarketplace.core.common.TimeSource
import com.serhii.tvmarketplace.core.data.FDroidAppSummary
import com.serhii.tvmarketplace.core.data.FDroidCatalog
import com.serhii.tvmarketplace.core.data.LookupResult
import com.serhii.tvmarketplace.core.data.PreviewResult
import com.serhii.tvmarketplace.core.data.ReleaseResult
import com.serhii.tvmarketplace.core.data.ReleaseSource
import com.serhii.tvmarketplace.core.domain.ApkManifestReader
import com.serhii.tvmarketplace.core.domain.AppPackage
import com.serhii.tvmarketplace.core.domain.AppPreview
import com.serhii.tvmarketplace.core.domain.AppSource
import com.serhii.tvmarketplace.core.domain.FDroidApp
import com.serhii.tvmarketplace.core.domain.Release
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.ContentConvertException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

internal const val FDROID_REPO = "https://f-droid.org/repo"
private const val FDROID_API = "https://f-droid.org/api/v1/packages"
private const val FDROID_SEARCH = "https://search.f-droid.org/api/search_apps"
private val SEARCH_CACHE_TTL: Duration = Duration.ofMinutes(10)

// an APK with native code for one or two architectures is a per-architecture build; with more it runs anywhere
private const val MAX_SPLIT_ABIS = 2

// f-droid.org's main repository, through the small JSON endpoints its website uses rather than its index, which is
// tens of megabytes
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class FDroidClient(private val client: HttpClient) : FDroidCatalog {

  private val searches = ConcurrentHashMap<String, Pair<Instant, List<FDroidAppSummary>>>()

  override suspend fun search(query: String): LookupResult<List<FDroidAppSummary>> {
    val key = query.lowercase()
    val now = Instant.now()
    searches[key]?.let { (at, apps) -> if (Duration.between(at, now) < SEARCH_CACHE_TTL) return LookupResult.Found(apps) }
    return lookup {
      val response = client.get(FDROID_SEARCH) { parameter("q", query) }
      if (response.status != HttpStatusCode.OK) return@lookup LookupResult.NetworkError
      val apps = response.body<SearchAppsDto>().apps.mapNotNull { it.toSummary() }
      searches[key] = now to apps
      LookupResult.Found(apps)
    }
  }

  override suspend fun preview(app: FDroidApp): PreviewResult = try {
    val response = client.get("$FDROID_API/${app.packageName}")
    when {
      response.status == HttpStatusCode.NotFound -> PreviewResult.NotFound
      response.status != HttpStatusCode.OK -> PreviewResult.NetworkError
      else -> {
        val versions = response.body<PackageVersionsDto>()
        // the name, summary and icon only come with search results; an app search doesn't find is shown by its package
        val summary = (search(app.packageName) as? LookupResult.Found)?.value?.firstOrNull { it.packageName == app.packageName }
        PreviewResult.Found(
          AppPreview(
            ref = app,
            displayName = summary?.name ?: app.packageName,
            description = summary?.summary,
            latestVersion = versions.latest(includeUnstable = false)?.versionName,
            avatarUrl = null,
            iconUrl = summary?.iconUrl,
          ),
        )
      }
    }
  } catch (e: CancellationException) {
    throw e
  } catch (_: IOException) {
    PreviewResult.NetworkError
  } catch (_: SerializationException) {
    PreviewResult.NetworkError
  } catch (_: ContentConvertException) {
    PreviewResult.NetworkError
  }

  private suspend fun <T> lookup(load: suspend () -> LookupResult<T>): LookupResult<T> = try {
    load()
  } catch (e: CancellationException) {
    throw e
  } catch (_: IOException) {
    LookupResult.NetworkError
  } catch (_: SerializationException) {
    LookupResult.NetworkError
  } catch (_: ContentConvertException) {
    LookupResult.NetworkError
  }
}

// F-Droid has no releases, only versions: the latest is the one it suggests, built once per architecture for some
// apps. Which build is for which architecture isn't published outside the index, so it's read from each APK's file
// list with ranged requests and written into the file name, where PackageChooser looks for it.
@Inject
class FDroidReleaseSource(
  private val client: HttpClient,
  private val timeSource: TimeSource,
) : ReleaseSource {

  override suspend fun latest(
    source: AppSource,
    includePrereleases: Boolean,
    etag: String?,
  ): ReleaseResult {
    val app = source as FDroidApp
    return try {
      val response = client.get("$FDROID_API/${app.packageName}") {
        if (etag != null) header(HttpHeaders.IfNoneMatch, etag)
      }
      when {
        response.status == HttpStatusCode.NotModified -> ReleaseResult.NotModified
        response.status == HttpStatusCode.NotFound -> ReleaseResult.SourceGone
        response.status != HttpStatusCode.OK -> ReleaseResult.NetworkError
        else -> {
          val versions = response.body<PackageVersionsDto>()
          val latest = versions.latest(includeUnstable = includePrereleases)
            ?: return ReleaseResult.Found(release = null, etag = response.headers[HttpHeaders.ETag])
          val builds = versions.packages.filter { it.versionName == latest.versionName }.map { it.versionCode }.distinct()
          val files = builds.map { code -> file(app.packageName, code, single = builds.size == 1) ?: return ReleaseResult.NetworkError }
          ReleaseResult.Found(
            release = Release(
              tag = latest.versionName,
              name = null,
              publishedAt = files.maxOfOrNull { it.second } ?: timeSource.now(),
              prerelease = latest.versionCode != versions.suggestedVersionCode,
              notesHtml = changelog(app.packageName, latest.versionCode),
              packages = files.map { it.first },
            ),
            etag = response.headers[HttpHeaders.ETag],
          )
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (_: IOException) {
      ReleaseResult.NetworkError
    } catch (_: SerializationException) {
      ReleaseResult.NetworkError
    } catch (_: ContentConvertException) {
      ReleaseResult.NetworkError
    }
  }

  // the build's size, its architectures and when it was published, from the end of the file; null when unreadable
  private suspend fun file(
    packageName: String,
    versionCode: Long,
    single: Boolean,
  ): Pair<AppPackage, Instant>? {
    val url = "$FDROID_REPO/${packageName}_$versionCode.apk"
    val tailResponse = client.get(url) { header(HttpHeaders.Range, "bytes=-${ApkManifestReader.TAIL_SIZE}") }
    if (tailResponse.status != HttpStatusCode.PartialContent) return null
    val size = tailResponse.headers[HttpHeaders.ContentRange]?.substringAfterLast('/')?.toLongOrNull() ?: return null
    val published = tailResponse.lastModified() ?: timeSource.now()
    val tail = tailResponse.readRawBytes()
    val abis = if (single) {
      emptySet()
    } else {
      val tailStart = size - tail.size
      val directory = ApkManifestReader.centralDirectory(tail, size) ?: return null
      val bytes = if (directory.offset >= tailStart) {
        val from = (directory.offset - tailStart).toInt()
        tail.copyOfRange(from, from + directory.size.toInt())
      } else {
        val response = client.get(url) { header(HttpHeaders.Range, "bytes=${directory.offset}-${directory.offset + directory.size - 1}") }
        if (response.status != HttpStatusCode.PartialContent) return null
        response.readRawBytes()
      }
      ApkManifestReader.nativeAbis(bytes).takeIf { it.size <= MAX_SPLIT_ABIS }.orEmpty()
    }
    val suffix = abis.sorted().joinToString("") { "_$it" }
    return AppPackage(
      id = versionCode,
      fileName = "${packageName}_$versionCode$suffix.apk",
      sizeBytes = size,
      downloadUrl = url,
    ) to published
  }

  // F-Droid keeps the "what's new" text of apps that publish it; shown as plain paragraphs
  private suspend fun changelog(
    packageName: String,
    versionCode: Long,
  ): String? {
    val response = client.get("$FDROID_REPO/$packageName/en-US/changelogs/$versionCode.txt")
    if (response.status != HttpStatusCode.OK) return null
    val text = response.bodyAsText().trim().takeIf { it.isNotEmpty() } ?: return null
    return text.split(Regex("\n\\s*\n")).joinToString("") { "<p>${escapeHtml(it.trim()).replace("\n", "<br>")}</p>" }
  }

  private fun HttpResponse.lastModified(): Instant? = try {
    headers[HttpHeaders.LastModified]?.let { Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(it)) }
  } catch (_: DateTimeException) {
    null
  }
}

private fun escapeHtml(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

@Serializable
private data class SearchAppsDto(val apps: List<SearchAppDto> = emptyList())

@Serializable
private data class SearchAppDto(
  val name: String? = null,
  val summary: String? = null,
  val icon: String? = null,
  val url: String? = null,
) {
  fun toSummary(): FDroidAppSummary? {
    val packageName = url?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.contains('.') } ?: return null
    return FDroidAppSummary(
      packageName = packageName,
      name = name?.takeIf { it.isNotBlank() } ?: packageName,
      summary = summary?.takeIf { it.isNotBlank() },
      iconUrl = icon?.takeIf { it.startsWith("https://") },
    )
  }
}

@Serializable
private data class PackageVersionsDto(
  val suggestedVersionCode: Long? = null,
  val packages: List<VersionDto> = emptyList(),
) {
  // the version F-Droid suggests is its latest stable one; newer ones are betas
  fun latest(includeUnstable: Boolean): VersionDto? = if (includeUnstable) {
    packages.maxByOrNull { it.versionCode }
  } else {
    packages.firstOrNull { it.versionCode == suggestedVersionCode } ?: packages.maxByOrNull { it.versionCode }
  }
}

@Serializable
private data class VersionDto(
  val versionName: String,
  val versionCode: Long,
)
