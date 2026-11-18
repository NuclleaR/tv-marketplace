package com.serhii.tvmarketplace.features.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.retain.retain
import androidx.navigation3.runtime.NavEntry
import com.serhii.tvmarketplace.core.common.rootGraphAs
import com.serhii.tvmarketplace.navigation.Destination
import com.serhii.tvmarketplace.navigation.NavEntryProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides

@ContributesTo(AppScope::class)
interface DetailGraph {
  val detailViewModelFactory: DetailViewModel.Factory
}

@BindingContainer
@ContributesTo(AppScope::class)
object DetailProvider {

  @Provides
  @IntoSet
  fun detailNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.Detail> { key ->
    NavEntry(key) {
      DetailScreen(itemId = key.itemId)
    }
  }
}

@Composable
fun DetailScreen(itemId: String) {
  val viewModel = retain(itemId) { rootGraphAs<DetailGraph>().detailViewModelFactory.create(itemId) }
  DetailView(viewState = viewModel.viewState())
}
