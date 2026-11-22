package com.serhii.tvmarketplace.core.linkserver

import com.serhii.tvmarketplace.core.common.AppLog
import com.serhii.tvmarketplace.core.data.IncomingLinksRepository
import com.serhii.tvmarketplace.core.data.TrackedAppsRepository
import com.serhii.tvmarketplace.core.domain.AppLinks
import com.serhii.tvmarketplace.core.domain.AppSource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

data class ReceivedLink(
  // grows with every received link, so the same link sent twice is still a new event
  val sequence: Long,
  val linkId: Long,
  val ref: AppSource,
  val senderLabel: String,
)

@SingleIn(AppScope::class)
@Inject
class IncomingLinkEvents {
  private val sequence = AtomicLong()
  private val last = MutableStateFlow<ReceivedLink?>(null)
  val lastReceived: StateFlow<ReceivedLink?> = last.asStateFlow()

  fun emit(
    linkId: Long,
    ref: AppSource,
    senderLabel: String,
  ) {
    last.value = ReceivedLink(sequence.incrementAndGet(), linkId, ref, senderLabel)
  }
}

sealed interface ReceiveResult {
  data class Stored(
    val linkId: Long,
    val ref: AppSource,
    val duplicate: Boolean,
  ) : ReceiveResult
  data object Unsupported : ReceiveResult
}

@Inject
class LinkReceiver(
  private val incomingLinks: IncomingLinksRepository,
  private val trackedApps: TrackedAppsRepository,
  private val events: IncomingLinkEvents,
) {

  suspend fun receive(
    text: String,
    senderPhoneId: String?,
    senderLabel: String,
  ): ReceiveResult {
    val ref = AppLinks.extractFirst(text) ?: return ReceiveResult.Unsupported
    val link = incomingLinks.receive(rawText = text, ref = ref, senderPhoneId = senderPhoneId, senderLabel = senderLabel)
    val duplicate = trackedApps.find(ref) != null
    events.emit(link.id, ref, senderLabel)
    AppLog.event("link_received", "duplicate" to if (duplicate) 1 else 0)
    return ReceiveResult.Stored(linkId = link.id, ref = ref, duplicate = duplicate)
  }
}

private const val MAX_LABEL = 60

// "Safari on iPhone", "Chrome on Android": enough to tell phones apart without storing the User-Agent
fun deviceLabel(userAgent: String?): String {
  val ua = userAgent.orEmpty()
  val device = when {
    "iPhone" in ua -> "iPhone"
    "iPad" in ua -> "iPad"
    "Android" in ua -> "Android"
    "Macintosh" in ua -> "Mac"
    "Windows" in ua -> "Windows"
    "Linux" in ua -> "Linux"
    else -> null
  }
  val browser = when {
    "SamsungBrowser" in ua -> "Samsung Internet"
    "Firefox" in ua || "FxiOS" in ua -> "Firefox"
    "Edg" in ua -> "Edge"
    "Chrome" in ua || "CriOS" in ua -> "Chrome"
    "Safari" in ua -> "Safari"
    else -> null
  }
  val label = listOfNotNull(browser, device).joinToString(" on ").ifEmpty { "Phone" }
  return label.take(MAX_LABEL)
}
