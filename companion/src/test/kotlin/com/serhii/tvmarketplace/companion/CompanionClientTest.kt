package com.serhii.tvmarketplace.companion

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class CompanionClientTest {

  private val tv = FoundTv("Living room", "tv-1", "192.168.1.42", 8765)
  private val requests = mutableListOf<HttpRequestData>()

  private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) = CompanionClient(
    HttpClient(
      MockEngine { request ->
        requests += request
        handler(request)
      },
    ),
  )

  private fun MockRequestHandleScope.json(
    status: HttpStatusCode,
    body: String,
  ) = respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

  @Test
  fun `sends the link with the device token to the TV's current address`() = runTest {
    val client = client { json(HttpStatusCode.Accepted, """{"linkId":1,"normalizedUrl":"https://github.com/a/b","duplicate":true}""") }
    assertEquals(SendResult.Sent(duplicate = true), client.send(tv, deviceToken = "token", text = "https://github.com/a/b"))
    val request = requests.single()
    assertEquals("http://192.168.1.42:8765/api/v1/links", request.url.toString())
    assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
    assertEquals("""{"text":"https://github.com/a/b"}""", (request.body as TextContent).text)
  }

  @Test
  fun `maps a revoked phone, an unsupported link and an unreachable TV`() = runTest {
    assertEquals(SendResult.Revoked, client { json(HttpStatusCode.Forbidden, """{"error":"device_revoked"}""") }.send(tv, "t", "x"))
    assertEquals(
      SendResult.Unsupported,
      client { json(HttpStatusCode.UnprocessableEntity, """{"error":"unsupported_link"}""") }.send(tv, "t", "x"),
    )
    assertEquals(SendResult.Unreachable, client { throw IOException("no route") }.send(tv, "t", "x"))
  }

  @Test
  fun `pairs with the pin and reports a wrong one`() = runTest {
    val ok = client { json(HttpStatusCode.OK, """{"deviceToken":"device","tvName":"Living room"}""") }
    assertEquals(PairResult.Paired(deviceToken = "device", tvName = "Living room"), ok.pair(tv, pin = "482913", deviceName = "Pixel 9"))
    val wrong = client { json(HttpStatusCode.Unauthorized, """{"error":"invalid_pin","attemptsLeft":2}""") }
    assertEquals(PairResult.WrongPin(attemptsLeft = 2), wrong.pair(tv, "000000", "Pixel 9"))
    val closed = client { json(HttpStatusCode.Gone, """{"error":"no_active_session"}""") }
    assertEquals(PairResult.NoSession, closed.pair(tv, "000000", "Pixel 9"))
  }
}
