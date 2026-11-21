package com.serhii.tvmarketplace.core.data

import com.serhii.tvmarketplace.core.domain.AppPackage
import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class UpdateCheckState(
  val running: Boolean,
  val lastFinishedAt: Instant?,
  // GitHub limited requests: no check runs before this
  val resumeAt: Instant?,
)

sealed interface CheckResult {
  data object Done : CheckResult

  data class RateLimited(val resumeAt: Instant) : CheckResult

  data object NetworkError : CheckResult
}

// asks the sources for each tracked app's latest release and stores what they say; never installs anything
interface UpdateChecker {
  fun state(): Flow<UpdateCheckState>

  // a check that is already running is joined, not started twice
  suspend fun checkAll(): CheckResult

  suspend fun check(appId: Long): CheckResult
}

// finds out which app a package is without downloading it, so an app installed before it was tracked is recognized
fun interface PackageIdentity {
  // the package name, or null when it can't be read this way
  suspend fun learn(pkg: AppPackage): String?
}
