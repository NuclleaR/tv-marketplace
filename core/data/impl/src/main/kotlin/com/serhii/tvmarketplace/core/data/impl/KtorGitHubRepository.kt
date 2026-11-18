package com.serhii.tvmarketplace.core.data.impl

import com.serhii.tvmarketplace.core.common.TimeSource
import com.serhii.tvmarketplace.core.data.GitHubRepository
import com.serhii.tvmarketplace.core.data.PreviewResult
import com.serhii.tvmarketplace.core.domain.AppPreview
import com.serhii.tvmarketplace.core.domain.GitHubRepoRef
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

fun gitHubHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
  expectSuccess = false
  install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
  install(HttpTimeout) { requestTimeoutMillis = 10_000 }
  defaultRequest {
    url("https://api.github.com/")
    header("Accept", "application/vnd.github+json")
    header("X-GitHub-Api-Version", "2022-11-28")
  }
}

private val CACHE_TTL: Duration = Duration.ofMinutes(10)

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class KtorGitHubRepository(
  private val client: HttpClient,
  private val timeSource: TimeSource,
) : GitHubRepository {

  private val cache = ConcurrentHashMap<String, Pair<Instant, AppPreview>>()

  override suspend fun preview(ref: GitHubRepoRef): PreviewResult {
    val now = timeSource.now()
    cache[ref.key]?.let { (cachedAt, preview) ->
      if (Duration.between(cachedAt, now) < CACHE_TTL) return PreviewResult.Found(preview)
    }
    return try {
      val repoResponse = client.get("repos/${ref.owner}/${ref.repo}")
      failure(repoResponse)?.let { return it }
      val repo = repoResponse.body<RepoDto>()
      val releasesResponse = client.get("repos/${ref.owner}/${ref.repo}/releases") { parameter("per_page", 5) }
      failure(releasesResponse)?.let { return it }
      val latest = releasesResponse.body<List<ReleaseDto>>().firstOrNull { !it.draft }?.tagName
      val preview = AppPreview(
        ref = GitHubRepoRef(owner = repo.owner.login, repo = repo.name),
        displayName = repo.name,
        description = repo.description,
        latestVersion = latest,
        avatarUrl = repo.owner.avatarUrl,
      )
      cache[ref.key] = now to preview
      PreviewResult.Found(preview)
    } catch (e: CancellationException) {
      throw e
    } catch (_: IOException) {
      PreviewResult.NetworkError
    } catch (_: SerializationException) {
      PreviewResult.NetworkError
    }
  }

  private fun failure(response: HttpResponse): PreviewResult? = when {
    response.status == HttpStatusCode.NotFound -> PreviewResult.NotFound
    response.headers["x-ratelimit-remaining"] == "0" -> PreviewResult.RateLimited
    response.status == HttpStatusCode.TooManyRequests -> PreviewResult.RateLimited
    response.status.value !in 200..299 -> PreviewResult.NetworkError
    else -> null
  }
}

@Serializable
private data class RepoDto(
  val name: String,
  val description: String? = null,
  val owner: OwnerDto,
)

@Serializable
private data class OwnerDto(
  val login: String,
  @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
private data class ReleaseDto(
  @SerialName("tag_name") val tagName: String,
  val draft: Boolean = false,
)
