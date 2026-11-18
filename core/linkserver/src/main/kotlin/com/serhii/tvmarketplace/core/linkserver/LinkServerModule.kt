package com.serhii.tvmarketplace.core.linkserver

import com.serhii.tvmarketplace.core.data.PairedPhoneKind
import com.serhii.tvmarketplace.core.protocol.API_VERSION
import com.serhii.tvmarketplace.core.protocol.ApiError
import com.serhii.tvmarketplace.core.protocol.ErrorResponse
import com.serhii.tvmarketplace.core.protocol.PairRequest
import com.serhii.tvmarketplace.core.protocol.PairResponse
import com.serhii.tvmarketplace.core.protocol.PinRequest
import com.serhii.tvmarketplace.core.protocol.PingResponse
import com.serhii.tvmarketplace.core.protocol.ProtocolJson
import com.serhii.tvmarketplace.core.protocol.SendLinkRequest
import com.serhii.tvmarketplace.core.protocol.SendLinkResponse
import com.serhii.tvmarketplace.core.protocol.SessionResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray
import kotlinx.serialization.SerializationException

fun interface PageAssets {
  fun read(name: String): ByteArray?
}

class LinkServerDeps(
  val sessions: SessionManager,
  val receiver: LinkReceiver,
  val pages: PageAssets,
  // "ip:port" the server is bound to: anything else in the Host header is a DNS-rebinding attempt
  val allowedHosts: () -> Set<String>,
  val rateLimiter: RateLimiter,
  val pairing: PairingManager,
  val tvName: () -> String,
)

val MisdirectedRequest = HttpStatusCode(421, "Misdirected Request")

private const val MAX_BODY_BYTES = 8 * 1024

private val UNSUPPORTED_EXAMPLES = listOf(
  "https://github.com/owner/repo",
  "https://github.com/owner/repo/releases",
)

fun Application.linkServerModule(deps: LinkServerDeps) {
  intercept(ApplicationCallPipeline.Plugins) {
    val call = context
    val host = call.request.headers[HttpHeaders.Host]
    if (host == null || host !in deps.allowedHosts()) {
      call.respond(MisdirectedRequest)
      finish()
      return@intercept
    }
    if (!deps.rateLimiter.tryAcquire(call.request.local.remoteAddress)) {
      call.respond(HttpStatusCode.TooManyRequests)
      finish()
    }
  }

  routing {
    get("/") { call.respondPage(deps, state = "none") }
    // the iOS Shortcut opens this with the shared link; the page only prefills it, the user still taps Send
    get("/send") { call.respondPage(deps, state = "none") }
    get("/s/{token}") {
      val token = call.parameters["token"].orEmpty()
      call.respondPage(deps, state = if (deps.sessions.isValid(token)) "valid" else "expired")
    }
    get("/assets/{name}") {
      val name = call.parameters["name"].orEmpty()
      val type = when (name.substringAfterLast('.')) {
        "js" -> ContentType.Application.JavaScript
        "css" -> ContentType.Text.CSS
        "json" -> ContentType.Application.Json
        else -> null
      }
      val bytes = deps.pages.read(name)
      if (type == null || bytes == null || '/' in name) call.respond(HttpStatusCode.NotFound) else call.respondBytes(bytes, type)
    }

    post("/api/v1/sessions/pin") {
      val request = call.receiveJson<PinRequest>() ?: return@post
      when (val result = deps.sessions.exchangePin(request.pin)) {
        is PinResult.Accepted -> call.respondJson(HttpStatusCode.OK, SessionResponse(result.token, result.expiresAt.toString()))
        is PinResult.Rejected -> call.respondJson(
          HttpStatusCode.Unauthorized,
          ErrorResponse(ApiError.INVALID_PIN, attemptsLeft = result.attemptsLeft),
        )
        PinResult.NoSession -> call.respondError(HttpStatusCode.Gone, ApiError.NO_ACTIVE_SESSION)
      }
    }

    post("/api/v1/pair") {
      val request = call.receiveJson<PairRequest>() ?: return@post
      when (val result = deps.sessions.exchangePin(request.pin)) {
        is PinResult.Accepted -> {
          val (token, _) = deps.pairing.pair(request.deviceName, PairedPhoneKind.COMPANION)
          call.respondJson(HttpStatusCode.OK, PairResponse(deviceToken = token, tvName = deps.tvName()))
        }
        is PinResult.Rejected -> call.respondJson(
          HttpStatusCode.Unauthorized,
          ErrorResponse(ApiError.INVALID_PIN, attemptsLeft = result.attemptsLeft),
        )
        PinResult.NoSession -> call.respondError(HttpStatusCode.Gone, ApiError.NO_ACTIVE_SESSION)
      }
    }

    get("/api/v1/ping") {
      val bearer = call.bearerToken()
      if (bearer != null && deps.pairing.authenticate(bearer) == null) {
        call.respondError(HttpStatusCode.Forbidden, ApiError.DEVICE_REVOKED)
        return@get
      }
      val paired = bearer != null
      call.respondJson(HttpStatusCode.OK, PingResponse(tvName = deps.tvName(), apiVersion = API_VERSION, paired = paired))
    }

    post("/api/v1/links") {
      val request = call.receiveJson<SendLinkRequest>() ?: return@post
      val bearer = call.bearerToken()
      val phone = bearer?.let { deps.pairing.authenticate(it) }
      if (bearer != null && phone == null) {
        call.respondError(HttpStatusCode.Forbidden, ApiError.DEVICE_REVOKED)
        return@post
      }
      val token = request.sessionToken
      if (phone == null && (token == null || !deps.sessions.isValid(token))) {
        call.respondError(HttpStatusCode.Unauthorized, ApiError.SESSION_EXPIRED)
        return@post
      }
      val label = phone?.displayName ?: deviceLabel(call.request.headers[HttpHeaders.UserAgent])
      when (val result = deps.receiver.receive(request.text, senderPhoneId = phone?.id, senderLabel = label)) {
        is ReceiveResult.Stored -> {
          // only a phone that just proved it saw the TV screen may become a remembered one
          val deviceToken = if (phone == null && request.pair) deps.pairing.pair(label, PairedPhoneKind.BROWSER).first else null
          call.respondJson(
            HttpStatusCode.Accepted,
            SendLinkResponse(
              linkId = result.linkId,
              normalizedUrl = result.ref.url,
              duplicate = result.duplicate,
              deviceToken = deviceToken,
            ),
          )
        }
        ReceiveResult.Unsupported -> call.respondJson(
          HttpStatusCode.UnprocessableEntity,
          ErrorResponse(ApiError.UNSUPPORTED_LINK, examples = UNSUPPORTED_EXAMPLES),
        )
      }
    }
  }
}

private suspend fun ApplicationCall.respondPage(
  deps: LinkServerDeps,
  state: String,
) {
  val html = deps.pages.read("index.html")?.decodeToString()
  if (html == null) {
    respond(HttpStatusCode.NotFound)
  } else {
    response.headers.append(HttpHeaders.CacheControl, "no-store")
    respondText(html.replace("{{SESSION_STATE}}", state), ContentType.Text.Html)
  }
}

private fun ApplicationCall.bearerToken(): String? =
  request.headers[HttpHeaders.Authorization]?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")?.trim()

internal suspend inline fun <reified T> ApplicationCall.receiveJson(): T? {
  val length = request.contentLength()
  if (length != null && length > MAX_BODY_BYTES) {
    respond(HttpStatusCode.PayloadTooLarge)
    return null
  }
  val bytes = receiveChannel().readBuffer(MAX_BODY_BYTES + 1L).readByteArray()
  if (bytes.size > MAX_BODY_BYTES) {
    respond(HttpStatusCode.PayloadTooLarge)
    return null
  }
  return try {
    ProtocolJson.decodeFromString<T>(bytes.decodeToString())
  } catch (_: SerializationException) {
    respondError(HttpStatusCode.BadRequest, ApiError.BAD_REQUEST)
    null
  } catch (_: IllegalArgumentException) {
    respondError(HttpStatusCode.BadRequest, ApiError.BAD_REQUEST)
    null
  }
}

internal suspend inline fun <reified T> ApplicationCall.respondJson(
  status: HttpStatusCode,
  body: T,
) {
  respondText(ProtocolJson.encodeToString(body), ContentType.Application.Json, status)
}

internal suspend fun ApplicationCall.respondError(
  status: HttpStatusCode,
  error: String,
) = respondJson(status, ErrorResponse(error))
