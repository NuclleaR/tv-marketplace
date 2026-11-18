package com.serhii.tvmarketplace.core.linkserver

import com.serhii.tvmarketplace.core.common.TimeSource
import java.time.Duration
import java.time.Instant

class RateLimiter(
  private val timeSource: TimeSource,
  private val limitPerMinute: Int,
) {
  private val requests = HashMap<String, ArrayDeque<Instant>>()

  @Synchronized
  fun tryAcquire(client: String): Boolean {
    val now = timeSource.now()
    val windowStart = now - Duration.ofMinutes(1)
    val recent = requests.getOrPut(client) { ArrayDeque() }
    while (recent.isNotEmpty() && !recent.first().isAfter(windowStart)) recent.removeFirst()
    if (recent.size >= limitPerMinute) return false
    recent.addLast(now)
    return true
  }
}
