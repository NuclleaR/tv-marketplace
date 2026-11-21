package com.serhii.tvmarketplace.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// short serial names keep the debug back stack extra readable: [{"type":"Preview","owner":"a","repo":"b"}]
@Serializable
sealed interface Destination : NavKey {

  @Serializable
  @SerialName("Home")
  data object Home : Destination

  @Serializable
  @SerialName("AddApp")
  data object AddApp : Destination

  // from a received link (linkId) or from the search (owner + repo)
  @Serializable
  @SerialName("Preview")
  data class Preview(
    val linkId: Long? = null,
    val owner: String? = null,
    val repo: String? = null,
  ) : Destination

  @Serializable
  @SerialName("Inbox")
  data object Inbox : Destination

  @Serializable
  @SerialName("Settings")
  data object Settings : Destination

  @Serializable
  @SerialName("AppDetails")
  data class AppDetails(val appId: Long) : Destination

  @Serializable
  @SerialName("ReleaseNotes")
  data class ReleaseNotes(val appId: Long) : Destination

  @Serializable
  @SerialName("UpdateFailures")
  data object UpdateFailures : Destination
}
