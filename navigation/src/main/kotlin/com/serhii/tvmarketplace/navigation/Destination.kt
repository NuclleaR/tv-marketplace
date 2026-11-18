package com.serhii.tvmarketplace.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// short serial names keep the debug back stack extra readable: [{"type":"Detail","itemId":"1"}]
@Serializable
sealed interface Destination : NavKey {

  @Serializable
  @SerialName("Home")
  data object Home : Destination

  @Serializable
  @SerialName("Detail")
  data class Detail(val itemId: String) : Destination
}
