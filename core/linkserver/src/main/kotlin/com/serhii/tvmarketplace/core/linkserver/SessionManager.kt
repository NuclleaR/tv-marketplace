package com.serhii.tvmarketplace.core.linkserver

import com.serhii.tvmarketplace.core.common.TimeSource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64

// the invitation behind the QR code: knowing the token or the PIN proves the phone's owner saw the TV screen
data class SendSession(
  val token: String,
  val pin: String,
  val createdAt: Instant,
  val expiresAt: Instant,
  val failedPinAttempts: Int = 0,
)

sealed interface PinResult {
  data class Accepted(
    val token: String,
    val expiresAt: Instant,
  ) : PinResult
  data class Rejected(val attemptsLeft: Int) : PinResult
  data object NoSession : PinResult
}

private val SESSION_LIFETIME: Duration = Duration.ofMinutes(10)
private const val MAX_PIN_ATTEMPTS = 5

@SingleIn(AppScope::class)
@Inject
class SessionManager(private val timeSource: TimeSource) {

  private val random = SecureRandom()
  private val session = MutableStateFlow<SendSession?>(null)
  val current: StateFlow<SendSession?> = session.asStateFlow()

  @Synchronized
  fun start(): SendSession {
    val now = timeSource.now()
    val new = SendSession(
      token = randomToken(bytes = 16),
      pin = random.nextInt(1_000_000).toString().padStart(6, '0'),
      createdAt = now,
      expiresAt = now + SESSION_LIFETIME,
    )
    session.value = new
    return new
  }

  @Synchronized
  fun refreshIfExpired(): SendSession? {
    val active = session.value ?: return null
    return if (active.isExpired()) start() else active
  }

  @Synchronized
  fun invalidate() {
    session.value = null
  }

  fun isValid(token: String): Boolean {
    val active = session.value ?: return false
    return !active.isExpired() && constantTimeEquals(active.token, token)
  }

  @Synchronized
  fun exchangePin(pin: String): PinResult {
    val active = session.value?.takeUnless { it.isExpired() } ?: return PinResult.NoSession
    if (constantTimeEquals(active.pin, pin)) return PinResult.Accepted(active.token, active.expiresAt)
    val failed = active.failedPinAttempts + 1
    if (failed >= MAX_PIN_ATTEMPTS) {
      val _ = start()
    } else {
      session.value = active.copy(failedPinAttempts = failed)
    }
    return PinResult.Rejected(attemptsLeft = MAX_PIN_ATTEMPTS - failed)
  }

  fun randomToken(bytes: Int): String {
    val buffer = ByteArray(bytes).also(random::nextBytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer)
  }

  private fun SendSession.isExpired() = !timeSource.now().isBefore(expiresAt)
}

internal fun constantTimeEquals(
  a: String,
  b: String,
): Boolean = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
