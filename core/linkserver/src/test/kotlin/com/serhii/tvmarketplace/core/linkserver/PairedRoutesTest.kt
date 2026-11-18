package com.serhii.tvmarketplace.core.linkserver

import com.serhii.tvmarketplace.core.protocol.ErrorResponse
import com.serhii.tvmarketplace.core.protocol.PingResponse
import com.serhii.tvmarketplace.core.protocol.ProtocolJson
import com.serhii.tvmarketplace.core.protocol.SendLinkResponse
import com.serhii.tvmarketplace.core.testing.FakeIncomingLinks
import com.serhii.tvmarketplace.core.testing.FakePairedPhones
import com.serhii.tvmarketplace.core.testing.FakeTrackedApps
import com.serhii.tvmarketplace.core.testing.MutableTimeSource
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private const val HOST = "192.168.1.42:8765"

class PairedRoutesTest {

  private val time = MutableTimeSource()
  private val sessions = SessionManager(time)
  private val phones = FakePairedPhones(time)
  private val pairing = PairingManager(sessions, phones)
  private val incomingLinks = FakeIncomingLinks()
  private val receiver = LinkReceiver(incomingLinks, FakeTrackedApps(), IncomingLinkEvents())
  private lateinit var http: HttpClient

  private fun routesTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
    application {
      linkServerModule(
        LinkServerDeps(
          sessions = sessions,
          receiver = receiver,
          pages = PageAssets { null },
          allowedHosts = { setOf(HOST) },
          rateLimiter = RateLimiter(time, limitPerMinute = 100),
          pairing = pairing,
          tvName = { "Living room TV" },
        ),
      )
    }
    http = createClient { defaultRequest { headers.append(HttpHeaders.Host, HOST) } }
    block()
  }

  private suspend fun send(
    body: String,
    deviceToken: String? = null,
  ): HttpResponse = http.post("/api/v1/links") {
    contentType(ContentType.Application.Json)
    deviceToken?.let { bearerAuth(it) }
    setBody(body)
  }

  @Test
  fun `pairing on a session send returns a device token that works later`() = routesTest {
    val session = sessions.start()
    val first = send("""{"text":"https://github.com/a/b","sessionToken":"${session.token}","pair":true}""")
    val deviceToken = assertNotNull(ProtocolJson.decodeFromString<SendLinkResponse>(first.bodyAsText()).deviceToken)
    sessions.invalidate()
    val later = send("""{"text":"https://github.com/c/d"}""", deviceToken = deviceToken)
    assertEquals(HttpStatusCode.Accepted, later.status)
    assertNull(ProtocolJson.decodeFromString<SendLinkResponse>(later.bodyAsText()).deviceToken)
    assertEquals(phones.phones.value.single().id, incomingLinks.links.value.last().senderPhoneId)
  }

  @Test
  fun `a send without pairing does not create a device`() = routesTest {
    val session = sessions.start()
    val response = send("""{"text":"https://github.com/a/b","sessionToken":"${session.token}"}""")
    assertNull(ProtocolJson.decodeFromString<SendLinkResponse>(response.bodyAsText()).deviceToken)
    assertEquals(emptyList(), phones.phones.value)
  }

  @Test
  fun `a revoked device is told so`() = routesTest {
    val response = send("""{"text":"https://github.com/a/b"}""", deviceToken = "revoked")
    assertEquals(HttpStatusCode.Forbidden, response.status)
    assertEquals("device_revoked", ProtocolJson.decodeFromString<ErrorResponse>(response.bodyAsText()).error)
  }

  @Test
  fun `ping reports the pairing state`() = routesTest {
    val (token, _) = pairing.pair("Phone", com.serhii.tvmarketplace.core.data.PairedPhoneKind.BROWSER)
    assertEquals(
      PingResponse(tvName = "Living room TV", apiVersion = 1, paired = true),
      ProtocolJson.decodeFromString(http.get("/api/v1/ping") { bearerAuth(token) }.bodyAsText()),
    )
    assertEquals(false, ProtocolJson.decodeFromString<PingResponse>(http.get("/api/v1/ping").bodyAsText()).paired)
    assertEquals(HttpStatusCode.Forbidden, http.get("/api/v1/ping") { bearerAuth("revoked") }.status)
  }
}
