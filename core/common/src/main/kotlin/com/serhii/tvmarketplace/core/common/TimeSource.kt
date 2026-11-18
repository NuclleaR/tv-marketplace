package com.serhii.tvmarketplace.core.common

import java.time.Instant

fun interface TimeSource {
  fun now(): Instant

  companion object {
    val System: TimeSource = TimeSource { Instant.now() }
  }
}
