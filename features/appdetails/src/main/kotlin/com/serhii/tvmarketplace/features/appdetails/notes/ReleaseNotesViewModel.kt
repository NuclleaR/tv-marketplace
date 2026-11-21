package com.serhii.tvmarketplace.features.appdetails.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.serhii.tvmarketplace.core.data.ReleasesRepository
import com.serhii.tvmarketplace.core.data.TrackedAppsRepository
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.flow.combine

data class ReleaseNotesViewState(
  val name: String,
  val version: String?,
  val notesHtml: String?,
)

@AssistedInject
class ReleaseNotesViewModel(
  private val trackedApps: TrackedAppsRepository,
  private val releases: ReleasesRepository,
  @Assisted private val appId: Long,
) {

  @Composable
  fun viewState(): ReleaseNotesViewState? {
    val state by remember {
      combine(trackedApps.observe(appId), releases.latestReleases()) { app, stored ->
        app?.let { ReleaseNotesViewState(it.displayName, stored[appId]?.release?.tag, stored[appId]?.release?.notesHtml) }
      }
    }.collectAsState(initial = null)
    return state
  }

  @AssistedFactory
  interface Factory {
    fun create(appId: Long): ReleaseNotesViewModel
  }
}
