package com.serhii.tvmarketplace.features.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.serhii.tvmarketplace.core.data.ItemRepository
import com.serhii.tvmarketplace.navigation.Destination
import com.serhii.tvmarketplace.navigation.Navigator
import dev.zacsweers.metro.Inject

@Inject
class HomeViewModel(
  private val repository: ItemRepository,
  private val navigator: Navigator,
) {

  @Composable
  fun viewState(): HomeViewState {
    val items by remember { repository.items() }.collectAsState(initial = null)
    val current = items ?: return HomeViewState.Loading
    return HomeViewState.Content(
      items = current.map { HomeViewState.Row(id = it.id, title = it.title, description = it.description) },
    )
  }

  fun onItemClick(id: String) {
    navigator.goTo(Destination.Detail(itemId = id))
  }
}
