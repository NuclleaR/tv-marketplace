package com.serhii.tvmarketplace.core.data.impl

import com.serhii.tvmarketplace.core.common.TimeSource
import com.serhii.tvmarketplace.core.data.ReleaseResult
import com.serhii.tvmarketplace.core.data.ReleaseSource
import com.serhii.tvmarketplace.core.domain.AppPackage
import com.serhii.tvmarketplace.core.domain.AppSource
import com.serhii.tvmarketplace.core.domain.PackageChooser
import com.serhii.tvmarketplace.core.domain.Release
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.ContentConvertException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.time.Instant

// the newest releases come first, and ten reach past a run of drafts and pre-releases
private const val RELEASES_PER_CHECK = 10

// GitHub asks clients to wait at least a minute after a secondary limit that doesn't say how long
private const val DEFAULT_RETRY_SECONDS = 60L

@ContributesBinding(AppScope::class)
@Inject
class GitHubReleaseSource(
  private val client: HttpClient,
  private val timeSource: TimeSource,
) : ReleaseSource {

  override suspend fun latest(
    source: AppSource,
    includePrereleases: Boolean,
    etag: String?,
  ): ReleaseResult {
    val gitHub = source as AppSource.GitHub
    return try {
      val response = client.get("repos/${gitHub.owner}/${gitHub.repo}/releases") {
        parameter("per_page", RELEASES_PER_CHECK)
        // body_html next to body: the notes come already rendered by GitHub
        header(HttpHeaders.Accept, "application/vnd.github.html+json")
        if (etag != null) header(HttpHeaders.IfNoneMatch, etag)
      }
      failure(response) ?: run {
        val latest = response.body<List<ReleaseWithAssetsDto>>().firstOrNull { !it.draft && (includePrereleases || !it.prerelease) }
        ReleaseResult.Found(release = latest?.toRelease(), etag = response.headers[HttpHeaders.ETag])
      }
    } catch (e: CancellationException) {
      throw e
    } catch (_: IOException) {
      ReleaseResult.NetworkError
    } catch (_: SerializationException) {
      ReleaseResult.NetworkError
    } catch (_: ContentConvertException) {
      // a body that isn't the JSON asked for, like an error page from a proxy
      ReleaseResult.NetworkError
    }
  }

  private fun failure(response: HttpResponse): ReleaseResult? {
    val status = response.status
    return when {
      status == HttpStatusCode.NotModified -> ReleaseResult.NotModified
      status == HttpStatusCode.NotFound -> ReleaseResult.SourceGone
      status == HttpStatusCode.Forbidden || status == HttpStatusCode.TooManyRequests -> {
        val reset = response.headers["x-ratelimit-reset"]?.toLongOrNull()
        val retryAfter = response.headers[HttpHeaders.RetryAfter]?.toLongOrNull()
        ReleaseResult.RateLimited(
          when {
            response.headers["x-ratelimit-remaining"] == "0" && reset != null -> Instant.ofEpochSecond(reset)
            else -> timeSource.now().plusSeconds(retryAfter ?: DEFAULT_RETRY_SECONDS)
          },
        )
      }
      status.value !in 200..299 -> ReleaseResult.NetworkError
      else -> null
    }
  }
}

@Serializable
private data class ReleaseWithAssetsDto(
  @SerialName("tag_name") val tagName: String,
  val name: String? = null,
  val draft: Boolean = false,
  val prerelease: Boolean = false,
  @SerialName("published_at") val publishedAt: String? = null,
  @SerialName("body_html") val bodyHtml: String? = null,
  val assets: List<AssetDto> = emptyList(),
) {
  fun toRelease() = Release(
    tag = tagName,
    name = name,
    publishedAt = publishedAt?.let(Instant::parse) ?: Instant.EPOCH,
    prerelease = prerelease,
    notesHtml = bodyHtml,
    packages = assets.filter { it.isApk() }.map { it.toPackage() },
  )
}

@Serializable
private data class AssetDto(
  val id: Long,
  val name: String,
  val size: Long,
  @SerialName("content_type") val contentType: String? = null,
  @SerialName("browser_download_url") val downloadUrl: String,
  val digest: String? = null,
) {
  fun isApk() = PackageChooser.isPackageFile(name, contentType)

  fun toPackage() = AppPackage(
    id = id,
    fileName = name,
    sizeBytes = size,
    downloadUrl = downloadUrl,
    sha256 = digest?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:"),
  )
}
