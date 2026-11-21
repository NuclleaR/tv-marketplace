package com.serhii.tvmarketplace.features.home

import com.serhii.tvmarketplace.core.domain.AppStatus
import com.serhii.tvmarketplace.core.installer.UpdateBatch
import java.time.Instant

data class HomeViewState(
  val apps: List<App>,
  val inboxCount: Int = 0,
  val notice: Notice? = null,
  val updates: Updates = Updates(),
) {
  data class App(
    val id: Long,
    val name: String,
    val ownerAndRepo: String,
    val iconUrl: String? = null,
    val packageName: String? = null,
    val status: AppStatus = AppStatus.NotInstalled,
  )

  data class Notice(
    val linkId: Long,
    val sender: String,
  )

  data class Updates(
    val count: Int = 0,
    val checking: Boolean = false,
    val lastCheckedAt: Instant? = null,
    // GitHub is limiting requests until then
    val resumeAt: Instant? = null,
    val batch: UpdateBatch? = null,
  ) {
    val visible: Boolean get() = count > 0 || batch != null || resumeAt != null
  }
}
