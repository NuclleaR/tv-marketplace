package com.serhii.tvmarketplace.core.linkserver

import com.serhii.tvmarketplace.core.data.PairedPhoneKind
import com.serhii.tvmarketplace.core.protocol.ErrorResponse
import com.serhii.tvmarketplace.core.protocol.PairResponse
import com.serhii.tvmarketplace.core.protocol.ProtocolJson
import com.serhii.tvmarketplace.core.testing.FakeIncomingLinks
import com.serhii.tvmarketplace.core.testing.FakePairedPhones
import com.serhii.tvmarketplace.core.testing.FakeTrackedApps
import com.serhii.tvmarketplace.core.testing.MutableTimeSource
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
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

private const val HOST = "192.168.1.42:8765"

class CompanionRoutesTest {

  private val time = MutableTimeSource()
  private val sessions = SessionManager(time)
  private val phones = FakePairedPhones(time)
  private val pairing = PairingManager(sessions, phones)
  private val incomingLinks = FakeIncomingLinks()
  private lateinit var http: HttpClient

  private fun routesTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
    application {
      linkServerModule(
        LinkServerDeps(
          sessions = sessions,
          receiver = LinkReceiver(incomingLinks, FakeTrackedApps(), IncomingLinkEvents()),
          pages = PageAssets { name -> if (name == "index.html") "<p>{{SESSION_STATE}}</p>".toByteArray() else null },
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

  private suspend fun pair(body: String): HttpResponse = http.post("/api/v1/pair") {
    contentType(ContentType.Application.Json)
    setBody(body)
  }

  @Test
  fun `pairs a companion with the pin`() = routesTest {
    val session = sessions.start()
    val response = pair("""{"pin":"${session.pin}","deviceName":"${"Pixel 9 ".repeat(10)}"}""")
    assertEquals(HttpStatusCode.OK, response.status)
    val body = ProtocolJson.decodeFromString<PairResponse>(response.bodyAsText())
    assertEquals("Living room TV", body.tvName)
    val phone = assertNotNull(pairing.authenticate(body.deviceToken))
    assertEquals(PairedPhoneKind.COMPANION, phone.kind)
    assertEquals(60, phone.displayName.length)
  }

  @Test
  fun `rejects a wrong pin and a missing session`() = routesTest {
    assertEquals(HttpStatusCode.Gone, pair("""{"pin":"123456","deviceName":"Pixel"}""").status)
    val session = sessions.start()
    val wrong = if (session.pin == "000000") "111111" else "000000"
    val rejected = pair("""{"pin":"$wrong","deviceName":"Pixel"}""")
    assertEquals(HttpStatusCode.Unauthorized, rejected.status)
    assertEquals("invalid_pin", ProtocolJson.decodeFromString<ErrorResponse>(rejected.bodyAsText()).error)
  }

  @Test
  fun `the prefill page serves the send page and never sends by itself`() = routesTest {
    assertEquals("<p>none</p>", http.get("/send?u=https%3A%2F%2Fgithub.com%2Fa%2Fb").bodyAsText())
    assertEquals(emptyList(), incomingLinks.links.value)
  }
}
