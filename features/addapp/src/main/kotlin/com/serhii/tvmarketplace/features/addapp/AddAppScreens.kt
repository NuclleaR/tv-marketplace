package com.serhii.tvmarketplace.features.addapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.retain.retain
import androidx.navigation3.runtime.NavEntry
import com.serhii.tvmarketplace.core.common.rootGraphAs
import com.serhii.tvmarketplace.features.addapp.preview.PreviewView
import com.serhii.tvmarketplace.features.addapp.preview.PreviewViewModel
import com.serhii.tvmarketplace.features.addapp.typeit.TypeItScreen
import com.serhii.tvmarketplace.features.addapp.typeit.TypeItViewModel
import com.serhii.tvmarketplace.navigation.Destination
import com.serhii.tvmarketplace.navigation.NavEntryProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides

@ContributesTo(AppScope::class)
interface AddAppGraph {
  val addAppViewModel: AddAppViewModel
  val previewViewModelFactory: PreviewViewModel.Factory
  val typeItViewModel: TypeItViewModel
}

@BindingContainer
@ContributesTo(AppScope::class)
object AddAppProvider {

  @Provides
  @IntoSet
  fun addAppNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.AddApp> { key ->
    NavEntry(key) { AddAppScreen() }
  }

  @Provides
  @IntoSet
  fun typeItNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.TypeIt> { key ->
    NavEntry(key) {
      val viewModel = retain { rootGraphAs<AddAppGraph>().typeItViewModel }
      TypeItScreen(viewModel)
    }
  }

  @Provides
  @IntoSet
  fun previewNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.Preview> { key ->
    NavEntry(key) { PreviewScreen(key) }
  }
}

@Composable
fun AddAppScreen() {
  val viewModel = retain { rootGraphAs<AddAppGraph>().addAppViewModel }
  AddAppView(viewState = viewModel.viewState(), onTypeIt = viewModel::onTypeIt, onRetry = viewModel::onRetry)
}

@Composable
fun PreviewScreen(destination: Destination.Preview) {
  val viewModel = retain(destination) { rootGraphAs<AddAppGraph>().previewViewModelFactory.create(destination) }
  PreviewView(
    viewState = viewModel.viewState(),
    onAdd = viewModel::onAdd,
    onOpen = viewModel::onOpen,
    onCancel = viewModel::onCancel,
    onRetry = viewModel::onRetry,
  )
}
