package com.serhii.tvmarketplace.core.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// the LAN API between a phone and the TV, see specs/001-phone-link-sharing/contracts/lan-http-api.md
const val API_VERSION = 1

val ProtocolJson: Json = Json {
  ignoreUnknownKeys = true
  explicitNulls = false
}

object ApiError {
  const val INVALID_PIN = "invalid_pin"
  const val NO_ACTIVE_SESSION = "no_active_session"
  const val SESSION_EXPIRED = "session_expired"
  const val DEVICE_REVOKED = "device_revoked"
  const val UNSUPPORTED_LINK = "unsupported_link"
  const val BAD_REQUEST = "bad_request"
}

@Serializable
data class PinRequest(val pin: String)

@Serializable
data class SessionResponse(
  val sessionToken: String,
  val expiresAt: String,
)

@Serializable
data class SendLinkRequest(
  val text: String,
  val sessionToken: String? = null,
  val pair: Boolean = false,
  val deviceName: String? = null,
)

@Serializable
data class SendLinkResponse(
  val linkId: Long,
  val normalizedUrl: String,
  val duplicate: Boolean,
  val deviceToken: String? = null,
)

@Serializable
data class PairRequest(
  val pin: String,
  val deviceName: String,
)

@Serializable
data class PairResponse(
  val deviceToken: String,
  val tvName: String,
)

@Serializable
data class PingResponse(
  val tvName: String,
  val apiVersion: Int,
  val paired: Boolean,
)

@Serializable
data class ErrorResponse(
  val error: String,
  val attemptsLeft: Int? = null,
  val examples: List<String>? = null,
)
