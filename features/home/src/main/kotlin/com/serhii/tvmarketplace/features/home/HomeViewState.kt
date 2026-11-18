package com.serhii.tvmarketplace.features.home

sealed interface HomeViewState {
  data object Loading : HomeViewState
  data class Content(val items: List<Row>) : HomeViewState

  data class Row(
    val id: String,
    val title: String,
    val description: String,
  )
}
