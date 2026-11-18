package com.serhii.tvmarketplace.core.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class ProtocolSerializationTest {

  @Test
  fun `send link request accepts the minimal body`() {
    val request = ProtocolJson.decodeFromString<SendLinkRequest>("""{"text":"https://github.com/a/b"}""")
    assertEquals(SendLinkRequest(text = "https://github.com/a/b"), request)
  }

  @Test
  fun `send link request ignores unknown fields`() {
    val request = ProtocolJson.decodeFromString<SendLinkRequest>(
      """{"text":"x","sessionToken":"t","pair":true,"deviceName":"Pixel","extra":1}""",
    )
    assertEquals(SendLinkRequest(text = "x", sessionToken = "t", pair = true, deviceName = "Pixel"), request)
  }

  @Test
  fun `send link response omits a missing device token`() {
    val json = ProtocolJson.encodeToString(SendLinkResponse(linkId = 17, normalizedUrl = "https://github.com/a/b", duplicate = false))
    assertEquals("""{"linkId":17,"normalizedUrl":"https://github.com/a/b","duplicate":false}""", json)
  }

  @Test
  fun `error response carries the contract code and optional details`() {
    assertEquals(
      """{"error":"invalid_pin","attemptsLeft":3}""",
      ProtocolJson.encodeToString(ErrorResponse(error = ApiError.INVALID_PIN, attemptsLeft = 3)),
    )
    assertEquals(
      """{"error":"unsupported_link","examples":["https://github.com/owner/repo"]}""",
      ProtocolJson.encodeToString(ErrorResponse(error = ApiError.UNSUPPORTED_LINK, examples = listOf("https://github.com/owner/repo"))),
    )
  }

  @Test
  fun `round trips every message`() {
    val messages = listOf(
      PinRequest(pin = "482913"),
      SessionResponse(sessionToken = "t", expiresAt = "2026-11-18T12:10:00Z"),
      PairRequest(pin = "482913", deviceName = "Pixel 9"),
      PairResponse(deviceToken = "d", tvName = "Living room TV"),
      PingResponse(tvName = "Living room TV", apiVersion = 1, paired = true),
    )
    assertEquals(messages[0], ProtocolJson.decodeFromString<PinRequest>(ProtocolJson.encodeToString(messages[0] as PinRequest)))
    assertEquals(messages[1], ProtocolJson.decodeFromString<SessionResponse>(ProtocolJson.encodeToString(messages[1] as SessionResponse)))
    assertEquals(messages[2], ProtocolJson.decodeFromString<PairRequest>(ProtocolJson.encodeToString(messages[2] as PairRequest)))
    assertEquals(messages[3], ProtocolJson.decodeFromString<PairResponse>(ProtocolJson.encodeToString(messages[3] as PairResponse)))
    assertEquals(messages[4], ProtocolJson.decodeFromString<PingResponse>(ProtocolJson.encodeToString(messages[4] as PingResponse)))
  }

  @Test
  fun `error codes match the contract`() {
    assertEquals(
      listOf("invalid_pin", "no_active_session", "session_expired", "device_revoked", "unsupported_link", "bad_request"),
      listOf(
        ApiError.INVALID_PIN,
        ApiError.NO_ACTIVE_SESSION,
        ApiError.SESSION_EXPIRED,
        ApiError.DEVICE_REVOKED,
        ApiError.UNSUPPORTED_LINK,
        ApiError.BAD_REQUEST,
      ),
    )
  }
}
