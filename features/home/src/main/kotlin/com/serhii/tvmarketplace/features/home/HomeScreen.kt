package com.serhii.tvmarketplace.features.home

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
interface HomeGraph {
  val homeViewModel: HomeViewModel
}

@BindingContainer
@ContributesTo(AppScope::class)
object HomeProvider {

  @Provides
  @IntoSet
  fun homeNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.Home> { key ->
    NavEntry(key) {
      HomeScreen()
    }
  }
}

@Composable
fun HomeScreen() {
  val viewModel = retain { rootGraphAs<HomeGraph>().homeViewModel }
  HomeView(
    viewState = viewModel.viewState(),
    onOpen = viewModel::onOpen,
    onOpenNotice = viewModel::onOpenNotice,
    onCheckForUpdates = viewModel::onCheckForUpdates,
    onUpdateAll = viewModel::onUpdateAll,
    onStopAfterCurrent = viewModel::onStopAfterCurrent,
    onShowFailures = viewModel::onShowFailures,
  )
}
