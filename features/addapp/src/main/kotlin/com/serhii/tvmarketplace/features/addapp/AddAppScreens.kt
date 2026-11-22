package com.serhii.tvmarketplace.features.addapp

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.navigation3.runtime.NavEntry
import androidx.tracing.Trace
import com.serhii.tvmarketplace.core.common.rootGraphAs
import com.serhii.tvmarketplace.features.addapp.preview.PreviewView
import com.serhii.tvmarketplace.features.addapp.preview.PreviewViewModel
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
  fun previewNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.Preview> { key ->
    NavEntry(key) { PreviewScreen(key) }
  }
}

private const val QR_SHOWN_TRACE = "AddApp:qrShown"

// GitHub owner and repository names together stay well under this
private const val MAX_QUERY_LENGTH = 140

@Composable
fun AddAppScreen() {
  val viewModel = retain { rootGraphAs<AddAppGraph>().addAppViewModel }
  LifecycleStartEffect(viewModel) {
    viewModel.onVisible()
    onStopOrDispose { viewModel.onHidden() }
  }
  // the :baselineprofile benchmark measures this section: from opening Add app to the QR on screen
  remember { Trace.beginAsyncSection(QR_SHOWN_TRACE, 0) }
  var query by rememberSaveable { mutableStateOf("") }
  val viewState = viewModel.viewState(query)
  if (viewState.phone is PhoneState.Ready) {
    LaunchedEffect(Unit) { Trace.endAsyncSection(QR_SHOWN_TRACE, 0) }
  }
  val context = LocalContext.current
  AddAppView(
    viewState = viewState,
    onCharacter = { if (query.length < MAX_QUERY_LENGTH) query += it },
    onBackspace = { query = query.dropLast(1) },
    onOwnerClick = { query = "$it/" },
    onRepoClick = viewModel::onRepoClick,
    onFDroidClick = viewModel::onFDroidClick,
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
