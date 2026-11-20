package com.serhii.tvmarketplace.core.data.impl

import com.serhii.tvmarketplace.core.common.TimeSource
import com.serhii.tvmarketplace.core.data.GitHubOwner
import com.serhii.tvmarketplace.core.data.LookupResult
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
import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KtorGitHubLookupTest {

  private var now = Instant.parse("2026-11-18T12:00:00Z")
  private val requests = mutableListOf<HttpRequestData>()

  private fun repository(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) = KtorGitHubRepository(
    client = gitHubHttpClient(
      MockEngine { request ->
        requests += request
        handler(request)
      },
    ),
    timeSource = TimeSource { now },
  )

  private fun MockRequestHandleScope.json(body: String) =
    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

  private fun repoJson(
    owner: String,
    name: String,
  ) = """{"name":"$name","owner":{"login":"$owner"},"description":"about $name","stargazers_count":3}"""

  @Test
  fun `searches owners by login and tells organizations apart`() = runTest {
    val repository = repository {
      json(
        """{"total_count":2,"items":[
          {"login":"NuclleaR","avatar_url":"https://a/1","type":"User"},
          {"login":"nucl-org","avatar_url":"https://a/2","type":"Organization"}]}""",
      )
    }
    val result = repository.searchOwners("nucl")
    assertEquals(
      LookupResult.Found(
        listOf(GitHubOwner("NuclleaR", "https://a/1", organization = false), GitHubOwner("nucl-org", "https://a/2", organization = true)),
      ),
      result,
    )
    assertEquals("/search/users", requests.single().url.encodedPath)
    assertEquals("nucl in:login", requests.single().url.parameters["q"])
  }

  @Test
  fun `a repeated search within ten minutes doesn't call GitHub again`() = runTest {
    val repository = repository { json("""{"items":[]}""") }
    val _ = repository.searchOwners("nucl")
    val _ = repository.searchOwners("NUCL")
    assertEquals(1, requests.size)
    now += Duration.ofMinutes(11)
    val _ = repository.searchOwners("nucl")
    assertEquals(2, requests.size)
  }

  @Test
  fun `lists an owner's repositories across pages until a short one`() = runTest {
    val repository = repository { request ->
      val page = request.url.parameters["page"]!!.toInt()
      val count = if (page == 1) 100 else 2
      json((1..count).joinToString(",", "[", "]") { repoJson("NuclleaR", "repo-$page-$it") })
    }
    val result = assertIs<LookupResult.Found<com.serhii.tvmarketplace.core.data.OwnerRepos>>(repository.ownerRepos("nucllear"))
    assertEquals(102, result.value.repos.size)
    assertEquals(true, result.value.complete)
    // the owner's login as GitHub spells it, not as typed
    assertEquals("NuclleaR", result.value.owner)
    assertEquals(listOf("1", "2"), requests.map { it.url.parameters["page"] })
    assertEquals("/users/nucllear/repos", requests.first().url.encodedPath)
  }

  @Test
  fun `an owner with more repositories than three pages is marked incomplete`() = runTest {
    val repository = repository { json((1..100).joinToString(",", "[", "]") { repoJson("google", "r$it") }) }
    val result = assertIs<LookupResult.Found<com.serhii.tvmarketplace.core.data.OwnerRepos>>(repository.ownerRepos("google"))
    assertEquals(300, result.value.repos.size)
    assertEquals(false, result.value.complete)
  }

  @Test
  fun `searches an owner's repositories by name`() = runTest {
    val repository = repository { json("""{"items":[${repoJson("google", "tv-samples")}]}""") }
    val result = repository.searchOwnerRepos("google", "tv")
    assertEquals(
      listOf("tv-samples"),
      assertIs<LookupResult.Found<List<*>>>(result).value.map {
        (it as com.serhii.tvmarketplace.core.data.GitHubRepoSummary).name
      },
    )
    assertEquals("tv in:name user:google", requests.single().url.parameters["q"])
  }

  @Test
  fun `maps missing owners, limits and network failures`() = runTest {
    assertEquals(LookupResult.NotFound, repository { respondError(HttpStatusCode.NotFound) }.ownerRepos("nobody"))
    assertEquals(LookupResult.NotFound, repository { respondError(HttpStatusCode.UnprocessableEntity) }.searchOwnerRepos("nobody", "x"))
    assertEquals(
      LookupResult.RateLimited,
      repository { respond("{}", HttpStatusCode.Forbidden, headersOf("x-ratelimit-remaining", "0")) }.searchOwners("nucl"),
    )
    assertEquals(LookupResult.NetworkError, repository { throw IOException("offline") }.searchOwners("nucl"))
  }

  @Test
  fun `failures aren't cached`() = runTest {
    var fail = true
    val repository = repository { if (fail) respondError(HttpStatusCode.InternalServerError) else json("""{"items":[]}""") }
    assertEquals(LookupResult.NetworkError, repository.searchOwners("nucl"))
    fail = false
    assertEquals(LookupResult.Found(emptyList()), repository.searchOwners("nucl"))
  }
}
