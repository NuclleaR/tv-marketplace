package com.serhii.tvmarketplace.core.data.impl

import com.serhii.tvmarketplace.core.common.TimeSource
import com.serhii.tvmarketplace.core.data.GitHubOwner
import com.serhii.tvmarketplace.core.data.GitHubRepoSummary
import com.serhii.tvmarketplace.core.data.GitHubRepository
import com.serhii.tvmarketplace.core.data.LookupResult
import com.serhii.tvmarketplace.core.data.OwnerRepos
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
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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

// three pages of 100 cover nearly every owner; for the rest, searchOwnerRepos finds what's typed
private const val OWNER_REPO_PAGES = 3
private const val PAGE_SIZE = 100

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class KtorGitHubRepository(
  private val client: HttpClient,
  private val timeSource: TimeSource,
) : GitHubRepository {

  private val cache = ConcurrentHashMap<String, Pair<Instant, AppPreview>>()

  // searches cost GitHub's tight search limit (10 a minute without a token): typing back and forth reuses answers
  private val lookups = ConcurrentHashMap<String, Pair<Instant, LookupResult.Found<*>>>()

  override suspend fun searchOwners(query: String): LookupResult<List<GitHubOwner>> = cached("owners:${query.lowercase()}") {
    val response = client.get("search/users") {
      parameter("q", "$query in:login")
      parameter("per_page", 30)
    }
    response.lookupFailure()?.let { return@cached it }
    LookupResult.Found(
      response.body<SearchDto<OwnerDto>>().items.map {
        GitHubOwner(login = it.login, avatarUrl = it.avatarUrl, organization = it.type == "Organization")
      },
    )
  }

  override suspend fun searchRepos(name: String): LookupResult<List<GitHubRepoSummary>> = cached("named:${name.lowercase()}") {
    val response = client.get("search/repositories") {
      parameter("q", "$name in:name")
      parameter("per_page", 20)
    }
    response.lookupFailure()?.let { return@cached it }
    LookupResult.Found(response.body<SearchDto<RepoSummaryDto>>().items.map { it.toSummary() })
  }

  override suspend fun ownerRepos(owner: String): LookupResult<OwnerRepos> = cached("repos:${owner.lowercase()}") {
    val repos = mutableListOf<GitHubRepoSummary>()
    var complete = false
    for (page in 1..OWNER_REPO_PAGES) {
      val response = client.get("users/$owner/repos") {
        parameter("type", "owner")
        parameter("sort", "pushed")
        parameter("per_page", PAGE_SIZE)
        parameter("page", page)
      }
      response.lookupFailure()?.let { return@cached it }
      val items = response.body<List<RepoSummaryDto>>()
      repos += items.map { it.toSummary() }
      if (items.size < PAGE_SIZE) {
        complete = true
        break
      }
    }
    LookupResult.Found(OwnerRepos(owner = repos.firstOrNull()?.owner ?: owner, repos = repos, complete = complete))
  }

  override suspend fun searchOwnerRepos(
    owner: String,
    name: String,
  ): LookupResult<List<GitHubRepoSummary>> = cached("search:${owner.lowercase()}/${name.lowercase()}") {
    val response = client.get("search/repositories") {
      parameter("q", "$name in:name user:$owner")
      parameter("per_page", 30)
    }
    response.lookupFailure()?.let { return@cached it }
    LookupResult.Found(response.body<SearchDto<RepoSummaryDto>>().items.map { it.toSummary() })
  }

  private suspend fun <T : Any> cached(
    key: String,
    load: suspend () -> LookupResult<T>,
  ): LookupResult<T> {
    val now = timeSource.now()
    lookups[key]?.let { (cachedAt, found) ->
      @Suppress("UNCHECKED_CAST")
      if (Duration.between(cachedAt, now) < CACHE_TTL) return found as LookupResult<T>
    }
    val result = try {
      load()
    } catch (e: CancellationException) {
      throw e
    } catch (_: IOException) {
      LookupResult.NetworkError
    } catch (_: SerializationException) {
      LookupResult.NetworkError
    }
    if (result is LookupResult.Found) lookups[key] = now to result
    return result
  }

  // search answers 422 for a query it can't run, such as an owner that doesn't exist: nothing found, not an error
  private fun HttpResponse.lookupFailure(): LookupResult<Nothing>? = when {
    status == HttpStatusCode.NotFound || status == HttpStatusCode.UnprocessableEntity -> LookupResult.NotFound
    headers["x-ratelimit-remaining"] == "0" || status == HttpStatusCode.TooManyRequests -> LookupResult.RateLimited
    status.value !in 200..299 -> LookupResult.NetworkError
    else -> null
  }

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
      val canonical = GitHubRepoRef(owner = repo.owner.login, repo = repo.name)
      val preview = AppPreview(
        ref = canonical,
        displayName = repo.name,
        description = repo.description,
        latestVersion = latest,
        avatarUrl = repo.owner.avatarUrl,
        iconUrl = fastlaneIcon(canonical, repo.defaultBranch) ?: launcherIcon(canonical, repo.defaultBranch),
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

  // many Android apps publish store metadata for F-Droid in this place; without it the owner's avatar is shown
  private suspend fun fastlaneIcon(
    ref: GitHubRepoRef,
    branch: String,
  ): String? {
    val root = "https://raw.githubusercontent.com/${ref.owner}/${ref.repo}/$branch/"
    val path = "fastlane/metadata/android/en-US/images/icon.png"
    return try {
      val response = client.head(root + path)
      when {
        response.status != HttpStatusCode.OK -> null
        response.isImage() -> root + path
        // raw.githubusercontent.com serves a git symlink as text holding its target, so follow it once
        response.contentType()?.match(ContentType.Text.Plain) == true -> {
          val target = resolve(path, client.get(root + path).bodyAsText().trim()) ?: return null
          (root + target).takeIf { client.head(it).let { r -> r.status == HttpStatusCode.OK && r.isImage() } }
        }
        else -> null
      }
    } catch (e: CancellationException) {
      throw e
    } catch (_: IOException) {
      null
    }
  }

  // otherwise the launcher icon from the sources, which nearly every Android app has as a bitmap somewhere; one
  // request for the whole file list
  private suspend fun launcherIcon(
    ref: GitHubRepoRef,
    branch: String,
  ): String? = try {
    val response = client.get("repos/${ref.owner}/${ref.repo}/git/trees/$branch") { parameter("recursive", 1) }
    if (response.status != HttpStatusCode.OK) {
      null
    } else {
      launcherIconPath(response.body<TreeDto>().tree.filter { it.type == "blob" }.map { it.path })
        ?.let { "https://raw.githubusercontent.com/${ref.owner}/${ref.repo}/$branch/$it" }
    }
  } catch (e: CancellationException) {
    throw e
  } catch (_: IOException) {
    null
  } catch (_: SerializationException) {
    null
  }

  private fun HttpResponse.isImage() = contentType()?.match(ContentType.Image.Any) == true

  // a relative symlink target against the link's directory; null if it leaves the repository or isn't a plain path
  private fun resolve(
    linkPath: String,
    target: String,
  ): String? {
    if (target.isEmpty() || target.length > 256 || target.startsWith("/") || target.any { it.isWhitespace() }) return null
    val segments = linkPath.split("/").dropLast(1).toMutableList()
    for (segment in target.split("/")) {
      when (segment) {
        "", "." -> Unit
        ".." -> segments.removeLastOrNull() ?: return null
        else -> segments += segment
      }
    }
    return segments.joinToString("/").ifEmpty { null }
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
  @SerialName("default_branch") val defaultBranch: String = "HEAD",
)

@Serializable
private data class OwnerDto(
  val login: String,
  @SerialName("avatar_url") val avatarUrl: String? = null,
  val type: String? = null,
)

@Serializable
private data class SearchDto<T>(val items: List<T> = emptyList())

@Serializable
private data class RepoSummaryDto(
  val name: String,
  val owner: OwnerDto,
  val description: String? = null,
  val fork: Boolean = false,
  val archived: Boolean = false,
  @SerialName("stargazers_count") val stars: Int = 0,
) {
  fun toSummary() = GitHubRepoSummary(
    owner = owner.login,
    name = name,
    description = description,
    fork = fork,
    archived = archived,
    stars = stars,
  )
}

@Serializable
private data class ReleaseDto(
  @SerialName("tag_name") val tagName: String,
  val draft: Boolean = false,
)

@Serializable
private data class TreeDto(val tree: List<TreeEntryDto> = emptyList())

@Serializable
private data class TreeEntryDto(
  val path: String,
  val type: String,
)
