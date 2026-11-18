package com.serhii.tvmarketplace.core.linkserver

import com.serhii.tvmarketplace.core.common.TimeSource
import com.serhii.tvmarketplace.core.domain.GitHubRepoRef
import com.serhii.tvmarketplace.core.protocol.ErrorResponse
import com.serhii.tvmarketplace.core.protocol.ProtocolJson
import com.serhii.tvmarketplace.core.protocol.SendLinkResponse
import com.serhii.tvmarketplace.core.protocol.SessionResponse
import com.serhii.tvmarketplace.core.testing.FakeIncomingLinks
import com.serhii.tvmarketplace.core.testing.FakePairedPhones
import com.serhii.tvmarketplace.core.testing.FakeTrackedApps
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val TV_HOST = "192.168.1.42:8765"

class LinkServerRoutesTest {

  private var now = Instant.parse("2026-11-18T12:00:00Z")
  private val time = TimeSource { now }
  private val sessions = SessionManager(time)
  private val incomingLinks = FakeIncomingLinks()
  private val trackedApps = FakeTrackedApps(GitHubRepoRef("already", "tracked"))
  private val events = IncomingLinkEvents()
  private val receiver = LinkReceiver(incomingLinks, trackedApps, events)
  private val pages = PageAssets { name -> if (name == "index.html") "<html>{{SESSION_STATE}}</html>".toByteArray() else null }

  private lateinit var http: HttpClient

  private fun routesTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
    http = createClient { defaultRequest { if (!headers.contains(HttpHeaders.Host)) headers.append(HttpHeaders.Host, TV_HOST) } }
    application {
      linkServerModule(
        LinkServerDeps(
          sessions = sessions,
          receiver = receiver,
          pages = pages,
          allowedHosts = { setOf(TV_HOST) },
          rateLimiter = RateLimiter(time, limitPerMinute = 30),
          pairing = PairingManager(sessions, FakePairedPhones()),
          tvName = { "TV" },
        ),
      )
    }
    block()
  }

  private suspend fun HttpClient.postJson(
    path: String,
    body: String,
  ): HttpResponse = post(path) {
    contentType(ContentType.Application.Json)
    setBody(body)
  }

  private suspend fun HttpResponse.error() = ProtocolJson.decodeFromString<ErrorResponse>(bodyAsText()).error

  @Test
  fun `serves the send page with the session state`() = routesTest {
    val session = sessions.start()
    assertEquals("<html>none</html>", http.get("/").bodyAsText())
    assertEquals("<html>valid</html>", http.get("/s/${session.token}").bodyAsText())
    now += Duration.ofMinutes(11)
    assertEquals("<html>expired</html>", http.get("/s/${session.token}").bodyAsText())
  }

  @Test
  fun `sends a link with the session token`() = routesTest {
    val session = sessions.start()
    val response = http.postJson(
      "/api/v1/links",
      """{"text":"see https://github.com/ImranR98/Obtainium","sessionToken":"${session.token}"}""",
    )
    assertEquals(HttpStatusCode.Accepted, response.status)
    assertEquals(
      SendLinkResponse(linkId = 1, normalizedUrl = "https://github.com/ImranR98/Obtainium", duplicate = false),
      ProtocolJson.decodeFromString(response.bodyAsText()),
    )
    assertEquals(1, incomingLinks.links.value.size)
    assertEquals(1L, events.lastReceived.value?.linkId)
  }

  @Test
  fun `flags a link to an app that is already tracked`() = routesTest {
    val session = sessions.start()
    val response = http.postJson("/api/v1/links", """{"text":"https://github.com/already/tracked","sessionToken":"${session.token}"}""")
    assertTrue(ProtocolJson.decodeFromString<SendLinkResponse>(response.bodyAsText()).duplicate)
  }

  @Test
  fun `rejects an expired session, an unsupported link and a malformed body`() = routesTest {
    val session = sessions.start()
    val unsupported = http.postJson("/api/v1/links", """{"text":"https://example.com/foo","sessionToken":"${session.token}"}""")
    assertEquals(HttpStatusCode.UnprocessableEntity, unsupported.status)
    assertEquals("unsupported_link", unsupported.error())
    assertEquals(HttpStatusCode.BadRequest, http.postJson("/api/v1/links", "not json").status)
    now += Duration.ofMinutes(11)
    val expired = http.postJson("/api/v1/links", """{"text":"https://github.com/a/b","sessionToken":"${session.token}"}""")
    assertEquals(HttpStatusCode.Unauthorized, expired.status)
    assertEquals("session_expired", expired.error())
    assertEquals(0, incomingLinks.links.value.size)
  }

  @Test
  fun `exchanges a pin for the session token`() = routesTest {
    assertEquals(HttpStatusCode.Gone, http.postJson("/api/v1/sessions/pin", """{"pin":"123456"}""").status)
    val session = sessions.start()
    val ok = http.postJson("/api/v1/sessions/pin", """{"pin":"${session.pin}"}""")
    assertEquals(session.token, ProtocolJson.decodeFromString<SessionResponse>(ok.bodyAsText()).sessionToken)
    val wrong = if (session.pin == "000000") "111111" else "000000"
    val rejected = http.postJson("/api/v1/sessions/pin", """{"pin":"$wrong"}""")
    assertEquals(HttpStatusCode.Unauthorized, rejected.status)
    assertEquals(4, ProtocolJson.decodeFromString<ErrorResponse>(rejected.bodyAsText()).attemptsLeft)
  }

  @Test
  fun `rejects a foreign host header`() = routesTest {
    val response = http.get("/") { header(HttpHeaders.Host, "evil.example:8765") }
    assertEquals(MisdirectedRequest, response.status)
  }

  @Test
  fun `rejects bodies over 8 KiB and never sends CORS headers`() = routesTest {
    val session = sessions.start()
    val big = http.postJson("/api/v1/links", """{"text":"${"x".repeat(9000)}","sessionToken":"${session.token}"}""")
    assertEquals(HttpStatusCode.PayloadTooLarge, big.status)
    val response = http.get("/") { header(HttpHeaders.Origin, "https://evil.example") }
    assertNull(response.headers[HttpHeaders.AccessControlAllowOrigin])
  }

  @Test
  fun `limits a client to 30 requests a minute`() = routesTest {
    repeat(30) { assertEquals(HttpStatusCode.OK, http.get("/").status) }
    assertEquals(HttpStatusCode.TooManyRequests, http.get("/").status)
    now += Duration.ofMinutes(1)
    assertEquals(HttpStatusCode.OK, http.get("/").status)
  }
}
