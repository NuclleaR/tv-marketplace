package com.serhii.tvmarketplace.features.appdetails

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.navigation3.runtime.NavEntry
import com.serhii.tvmarketplace.core.common.rootGraphAs
import com.serhii.tvmarketplace.features.appdetails.notes.ReleaseNotesView
import com.serhii.tvmarketplace.features.appdetails.notes.ReleaseNotesViewModel
import com.serhii.tvmarketplace.navigation.Destination
import com.serhii.tvmarketplace.navigation.NavEntryProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides

@ContributesTo(AppScope::class)
interface AppDetailsGraph {
  val appDetailsViewModelFactory: AppDetailsViewModel.Factory
  val releaseNotesViewModelFactory: ReleaseNotesViewModel.Factory
}

@BindingContainer
@ContributesTo(AppScope::class)
object AppDetailsProvider {

  @Provides
  @IntoSet
  fun appDetailsNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.AppDetails> { key ->
    NavEntry(key) { AppDetailsScreen(key) }
  }

  @Provides
  @IntoSet
  fun releaseNotesNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.ReleaseNotes> { key ->
    NavEntry(key) { ReleaseNotesScreen(key) }
  }
}

@Composable
fun AppDetailsScreen(destination: Destination.AppDetails) {
  val viewModel = retain(destination) { rootGraphAs<AppDetailsGraph>().appDetailsViewModelFactory.create(destination.appId) }
  // coming back from the permission screen or from another app: refresh, and go on with a waiting install
  LifecycleStartEffect(viewModel) {
    viewModel.onVisible()
    onStopOrDispose { }
  }
  val context = LocalContext.current
  AppDetailsView(
    viewState = viewModel.viewState(),
    onPrimary = viewModel::onPrimary,
    onPackage = viewModel::onPackage,
    onReleaseNotes = viewModel::onReleaseNotes,
    onSettings = viewModel::onSettings,
    onRemove = viewModel::onRemove,
    onOpenPermissionSettings = { startFirst(context, viewModel.permissionIntents()) },
    onPermissionLater = viewModel::onPermissionLater,
    onUseAutomaticChoice = viewModel::onUseAutomaticChoice,
    onTogglePrereleases = viewModel::onTogglePrereleases,
    onConfirmRemove = viewModel::onConfirmRemove,
    onDismissDialog = viewModel::onDismissDialog,
  )
}

// some TVs lack the first screen; the next one is where the permission lives there
private fun startFirst(
  context: Context,
  intents: List<Intent>,
) {
  for (intent in intents) {
    try {
      context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      return
    } catch (_: ActivityNotFoundException) {
      continue
    }
  }
}

@Composable
fun ReleaseNotesScreen(destination: Destination.ReleaseNotes) {
  val viewModel = retain(destination) { rootGraphAs<AppDetailsGraph>().releaseNotesViewModelFactory.create(destination.appId) }
  ReleaseNotesView(viewState = viewModel.viewState())
}
