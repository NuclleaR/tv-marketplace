package com.serhii.tvmarketplace.features.addapp

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
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
  LifecycleStartEffect(viewModel) {
    viewModel.onVisible()
    onStopOrDispose { viewModel.onHidden() }
  }
  val context = LocalContext.current
  AddAppView(
    viewState = viewModel.viewState(),
    onTypeIt = viewModel::onTypeIt,
    onRetry = viewModel::onRetry,
    onNetworkSettings = { openNetworkSettings(context) },
  )
}

// some TVs have no Wi-Fi settings screen of their own, only the general one
private fun openNetworkSettings(context: Context) {
  for (action in listOf(Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_SETTINGS)) {
    try {
      context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      return
    } catch (_: ActivityNotFoundException) {
      continue
    }
  }
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
