package com.serhii.tvmarketplace.companion

import com.serhii.tvmarketplace.core.protocol.ErrorResponse
import com.serhii.tvmarketplace.core.protocol.PairRequest
import com.serhii.tvmarketplace.core.protocol.PairResponse
import com.serhii.tvmarketplace.core.protocol.ProtocolJson
import com.serhii.tvmarketplace.core.protocol.SendLinkRequest
import com.serhii.tvmarketplace.core.protocol.SendLinkResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import java.io.IOException

sealed interface SendResult {
  data class Sent(val duplicate: Boolean) : SendResult
  data object Unsupported : SendResult
  data object Revoked : SendResult
  data object Unreachable : SendResult
}

sealed interface PairResult {
  data class Paired(
    val deviceToken: String,
    val tvName: String,
  ) : PairResult
  data class WrongPin(val attemptsLeft: Int) : PairResult
  data object NoSession : PairResult
  data object Unreachable : PairResult
}

class CompanionClient(private val http: HttpClient) {

  suspend fun send(
    tv: FoundTv,
    deviceToken: String,
    text: String,
  ): SendResult = request {
    val response = post(tv, "/api/v1/links", ProtocolJson.encodeToString(SendLinkRequest(text = text)), deviceToken)
    when (response.status) {
      HttpStatusCode.Accepted -> SendResult.Sent(ProtocolJson.decodeFromString<SendLinkResponse>(response.bodyAsText()).duplicate)
      HttpStatusCode.Forbidden, HttpStatusCode.Unauthorized -> SendResult.Revoked
      HttpStatusCode.UnprocessableEntity -> SendResult.Unsupported
      else -> SendResult.Unreachable
    }
  } ?: SendResult.Unreachable

  suspend fun pair(
    tv: FoundTv,
    pin: String,
    deviceName: String,
  ): PairResult = request {
    val response = post(tv, "/api/v1/pair", ProtocolJson.encodeToString(PairRequest(pin = pin, deviceName = deviceName)), null)
    when (response.status) {
      HttpStatusCode.OK -> ProtocolJson.decodeFromString<PairResponse>(response.bodyAsText()).let {
        PairResult.Paired(it.deviceToken, it.tvName)
      }
      HttpStatusCode.Unauthorized -> PairResult.WrongPin(
        ProtocolJson.decodeFromString<ErrorResponse>(response.bodyAsText()).attemptsLeft ?: 0,
      )
      HttpStatusCode.Gone -> PairResult.NoSession
      else -> PairResult.Unreachable
    }
  } ?: PairResult.Unreachable

  private suspend fun post(
    tv: FoundTv,
    path: String,
    json: String,
    deviceToken: String?,
  ): HttpResponse = http.post("http://${tv.host}:${tv.port}$path") {
    deviceToken?.let { bearerAuth(it) }
    setBody(TextContent(json, ContentType.Application.Json))
  }

  private suspend fun <T> request(block: suspend () -> T): T? = try {
    block()
  } catch (e: CancellationException) {
    throw e
  } catch (_: IOException) {
    null
  } catch (_: SerializationException) {
    null
  }
}
