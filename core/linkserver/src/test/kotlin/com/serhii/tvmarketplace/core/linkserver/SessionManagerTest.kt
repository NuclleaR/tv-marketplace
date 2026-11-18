package com.serhii.tvmarketplace.core.linkserver

import com.serhii.tvmarketplace.core.common.TimeSource
import java.time.Duration
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionManagerTest {

  private var now = Instant.parse("2026-11-18T12:00:00Z")
  private val manager = SessionManager(TimeSource { now })

  @Test
  fun `a session has a 128-bit url-safe token, a 6-digit pin and expires after 10 minutes`() {
    val session = manager.start()
    assertEquals(16, Base64.getUrlDecoder().decode(session.token).size)
    assertTrue(session.pin.matches(Regex("\\d{6}")))
    assertEquals(now + Duration.ofMinutes(10), session.expiresAt)
    assertEquals(session, manager.current.value)
  }

  @Test
  fun `only the current, unexpired token is valid`() {
    val first = manager.start()
    val second = manager.start()
    assertTrue(!manager.isValid(first.token))
    assertTrue(manager.isValid(second.token))
    now += Duration.ofMinutes(10)
    assertTrue(!manager.isValid(second.token))
  }

  @Test
  fun `rotates an expired session on refresh`() {
    val first = manager.start()
    assertEquals(first, manager.refreshIfExpired())
    now += Duration.ofMinutes(10)
    assertNotEquals(first.token, manager.refreshIfExpired()?.token)
  }

  @Test
  fun `exchanges the pin for the token and counts failures`() {
    val session = manager.start()
    assertEquals(PinResult.Accepted(session.token, session.expiresAt), manager.exchangePin(session.pin))
    val wrong = if (session.pin == "000000") "111111" else "000000"
    assertEquals(PinResult.Rejected(attemptsLeft = 4), manager.exchangePin(wrong))
  }

  @Test
  fun `five failed pins rotate the session`() {
    val session = manager.start()
    val wrong = if (session.pin == "000000") "111111" else "000000"
    repeat(4) { val _ = manager.exchangePin(wrong) }
    assertEquals(PinResult.Rejected(attemptsLeft = 0), manager.exchangePin(wrong))
    assertNotEquals(session.token, manager.current.value?.token)
    assertTrue(!manager.isValid(session.token))
  }

  @Test
  fun `no session after invalidation`() {
    val _ = manager.start()
    manager.invalidate()
    assertNull(manager.current.value)
    assertEquals(PinResult.NoSession, manager.exchangePin("123456"))
  }
}
