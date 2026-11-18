package com.serhii.tvmarketplace.features.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.serhii.tvmarketplace.core.data.Item
import com.serhii.tvmarketplace.core.data.ItemRepository
import com.serhii.tvmarketplace.navigation.Navigator
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.flow.map

@AssistedInject
class DetailViewModel(
  private val repository: ItemRepository,
  private val navigator: Navigator,
  @Assisted private val itemId: String,
) {

  @Composable
  fun viewState(): DetailViewState {
    val state by remember {
      repository.item(itemId).map<Item?, DetailViewState> { item ->
        if (item == null) DetailViewState.Missing else DetailViewState.Content(title = item.title, description = item.description)
      }
    }.collectAsState(initial = DetailViewState.Loading)
    return state
  }

  fun onBack() {
    navigator.goBack()
  }

  @AssistedFactory
  interface Factory {
    fun create(itemId: String): DetailViewModel
  }
}
