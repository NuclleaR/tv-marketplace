package com.serhii.tvmarketplace.features.home

data class HomeViewState(
  val apps: List<App>,
  val inboxCount: Int = 0,
  val notice: Notice? = null,
) {
  data class App(
    val id: Long,
    val name: String,
    val ownerAndRepo: String,
    val iconUrl: String? = null,
  )

  data class Notice(
    val linkId: Long,
    val sender: String,
  )
}
