package com.serhii.tvmarketplace.features.addapp

sealed interface AddAppViewState {
  data object Loading : AddAppViewState
  data object NoNetwork : AddAppViewState
  data object Failed : AddAppViewState
  data class Ready(
    val qrContent: String,
    val address: String,
    // grouped for reading at a distance: "482 913"
    val pin: String,
  ) : AddAppViewState
}
