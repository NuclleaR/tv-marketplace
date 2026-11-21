package com.serhii.tvmarketplace.features.addapp.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import com.serhii.tvmarketplace.core.data.GitHubRepository
import com.serhii.tvmarketplace.core.data.IncomingLinksRepository
import com.serhii.tvmarketplace.core.data.PreviewResult
import com.serhii.tvmarketplace.core.data.TrackedAppsRepository
import com.serhii.tvmarketplace.core.data.UpdateChecker
import com.serhii.tvmarketplace.core.domain.AppPreview
import com.serhii.tvmarketplace.core.domain.GitHubRepoRef
import com.serhii.tvmarketplace.navigation.Destination
import com.serhii.tvmarketplace.navigation.Navigator
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val ADDED_CONFIRMATION_MS = 1_500L

@AssistedInject
class PreviewViewModel(
  private val gitHub: GitHubRepository,
  private val trackedApps: TrackedAppsRepository,
  private val incomingLinks: IncomingLinksRepository,
  private val navigator: Navigator,
  private val updateChecker: UpdateChecker,
  // the first check of a new app outlives this screen
  private val appScope: CoroutineScope,
  @Assisted private val destination: Destination.Preview,
) {

  private var trackedId: Long? = null

  private var reloads by mutableIntStateOf(0)
  private var addRequested by mutableStateOf(false)
  private var added by mutableStateOf<String?>(null)
  private var loaded: AppPreview? = null

  @Composable
  fun viewState(): PreviewViewState {
    val state by produceState<PreviewViewState>(PreviewViewState.Loading, reloads) {
      value = PreviewViewState.Loading
      value = load()
    }
    LaunchedEffect(addRequested) {
      val preview = loaded
      if (addRequested && preview != null) {
        val app = trackedApps.add(preview)
        destination.linkId?.let { incomingLinks.markAdded(it) }
        // so Home shows right away whether there is something to install
        appScope.launch { val _ = updateChecker.check(app.id) }
        added = app.displayName
      }
    }
    LaunchedEffect(added) {
      if (added != null) {
        delay(ADDED_CONFIRMATION_MS)
        navigator.goBack()
      }
    }
    return added?.let { PreviewViewState.Added(it) } ?: state
  }

  private suspend fun load(): PreviewViewState {
    val link = destination.linkId?.let { id ->
      incomingLinks.get(id)
        ?: return PreviewViewState.Error(PreviewViewState.ErrorKind.LinkMissing)
    }
    val ref = link?.ref ?: GitHubRepoRef(owner = destination.owner.orEmpty(), repo = destination.repo.orEmpty())
    return when (val result = gitHub.preview(ref)) {
      is PreviewResult.Found -> {
        loaded = result.preview
        PreviewViewState.Content(
          name = result.preview.displayName,
          ownerAndRepo = "${result.preview.ref.owner}/${result.preview.ref.repo}",
          description = result.preview.description,
          latestVersion = result.preview.latestVersion,
          avatarUrl = result.preview.avatarUrl,
          iconUrl = result.preview.iconUrl,
          sender = link?.senderLabel,
          alreadyTracked = trackedApps.find(result.preview.ref)?.also { trackedId = it.id } != null,
        )
      }
      PreviewResult.NotFound -> PreviewViewState.Error(PreviewViewState.ErrorKind.NotFound)
      PreviewResult.RateLimited -> PreviewViewState.Error(PreviewViewState.ErrorKind.RateLimited)
      PreviewResult.NetworkError -> PreviewViewState.Error(PreviewViewState.ErrorKind.Network)
    }
  }

  fun onAdd() {
    addRequested = true
  }

  // the app is tracked already: its details instead of this preview
  fun onOpen() {
    val id = trackedId ?: return navigator.goBack()
    navigator.goBack()
    navigator.goTo(Destination.AppDetails(id))
  }

  fun onCancel() {
    navigator.goBack()
  }

  fun onRetry() {
    reloads++
  }

  @AssistedFactory
  interface Factory {
    fun create(destination: Destination.Preview): PreviewViewModel
  }
}
