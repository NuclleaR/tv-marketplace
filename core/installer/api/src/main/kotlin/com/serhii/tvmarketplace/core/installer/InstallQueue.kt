package com.serhii.tvmarketplace.core.installer

import com.serhii.tvmarketplace.core.domain.AppPackage
import com.serhii.tvmarketplace.core.domain.FailureReason
import com.serhii.tvmarketplace.core.domain.Release
import kotlinx.coroutines.flow.StateFlow

data class InstallRequest(
  val appId: Long,
  val release: Release,
  val pkg: AppPackage,
  // false when the user picked another package than the recommended one: that choice is remembered
  val recommended: Boolean,
)

// what the queue is doing for one app; no entry once it's done
sealed interface InstallJob {
  data class Downloading(val fraction: Float?) : InstallJob

  // handed to the system, waiting for its prompt or its result
  data object Installing : InstallJob

  data object NeedsPermission : InstallJob

  data class Failed(val reason: FailureReason) : InstallJob
}

// "Update all": done and total count every app, failed ones included
data class UpdateBatch(
  val total: Int,
  val done: Int,
  val failed: Map<Long, FailureReason>,
  val stopping: Boolean,
  val running: Boolean,
)

// downloads, verifies and installs, one app at a time, and records what was installed
interface InstallQueue {
  val jobs: StateFlow<Map<Long, InstallJob>>

  val batch: StateFlow<UpdateBatch?>

  fun install(request: InstallRequest)

  fun updateAll(requests: List<InstallRequest>)

  // finishes the app being installed, drops the rest of "Update all"
  fun stopAfterCurrent()

  fun cancel(appId: Long)

  // forgets a failure, and the end result of "Update all"
  fun dismiss(appId: Long)

  fun dismissBatch()
}
