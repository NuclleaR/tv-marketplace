package com.serhii.tvmarketplace.features.home.failures

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.tv.material3.Text
import com.serhii.tvmarketplace.core.common.rootGraphAs
import com.serhii.tvmarketplace.core.data.TrackedAppsRepository
import com.serhii.tvmarketplace.core.domain.FailureReason
import com.serhii.tvmarketplace.core.installer.InstallQueue
import com.serhii.tvmarketplace.core.ui.AppIcon
import com.serhii.tvmarketplace.core.ui.AppTheme
import com.serhii.tvmarketplace.core.ui.ListRow
import com.serhii.tvmarketplace.core.ui.PreviewTv
import com.serhii.tvmarketplace.core.ui.ScreenTitle
import com.serhii.tvmarketplace.core.ui.TvScreen
import com.serhii.tvmarketplace.core.ui.failureText
import com.serhii.tvmarketplace.navigation.Destination
import com.serhii.tvmarketplace.navigation.NavEntryProvider
import com.serhii.tvmarketplace.navigation.Navigator
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.flow.combine
import com.serhii.tvmarketplace.core.strings.R as StringsR

data class UpdateFailure(
  val appId: Long,
  val name: String,
  val iconUrl: String?,
  val reason: FailureReason,
)

@Inject
class UpdateFailuresViewModel(
  private val queue: InstallQueue,
  private val trackedApps: TrackedAppsRepository,
  private val navigator: Navigator,
) {

  @Composable
  fun viewState(): List<UpdateFailure> {
    val failures by remember {
      combine(queue.batch, trackedApps.trackedApps()) { batch, apps ->
        batch?.failed.orEmpty().mapNotNull { (id, reason) ->
          apps.firstOrNull { it.id == id }?.let { UpdateFailure(id, it.displayName, it.iconUrl, reason) }
        }
      }
    }.collectAsState(initial = emptyList())
    return failures
  }

  fun onOpen(appId: Long) {
    navigator.goTo(Destination.AppDetails(appId))
  }
}

@Composable
internal fun UpdateFailuresView(
  failures: List<UpdateFailure>,
  onOpen: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  val first = remember { FocusRequester() }
  TvScreen(modifier) {
    ScreenTitle(stringResource(StringsR.string.home_failures_title))
    LazyColumn(
      modifier = Modifier.widthIn(max = 840.dp).focusRestorer(first),
      contentPadding = PaddingValues(top = 24.dp, bottom = 27.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      items(failures, key = { it.appId }) { failure ->
        ListRow(
          onClick = { onOpen(failure.appId) },
          modifier = (if (failure == failures.first()) Modifier.focusRequester(first) else Modifier).fillMaxWidth(),
          leading = { AppIcon(failure.name, failure.iconUrl, size = 40.dp) },
          headline = { Text(failure.name, fontWeight = FontWeight.Medium) },
          supporting = { Text(failureText(failure.reason)) },
        )
      }
    }
  }
  LaunchedEffect(failures.isNotEmpty()) { if (failures.isNotEmpty()) first.requestFocus() }
}

@ContributesTo(AppScope::class)
interface UpdateFailuresGraph {
  val updateFailuresViewModel: UpdateFailuresViewModel
}

@BindingContainer
@ContributesTo(AppScope::class)
object UpdateFailuresProvider {

  @Provides
  @IntoSet
  fun updateFailuresNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.UpdateFailures> { key ->
    NavEntry(key) { UpdateFailuresScreen() }
  }
}

@Composable
fun UpdateFailuresScreen() {
  val viewModel = retain { rootGraphAs<UpdateFailuresGraph>().updateFailuresViewModel }
  UpdateFailuresView(failures = viewModel.viewState(), onOpen = viewModel::onOpen)
}

@PreviewTv
@Composable
private fun UpdateFailuresPreview() {
  AppTheme {
    UpdateFailuresView(
      listOf(
        UpdateFailure(1, "SmartTube", null, FailureReason.SignatureConflict),
        UpdateFailure(2, "Kodi", null, FailureReason.Network),
      ),
      onOpen = {},
    )
  }
}
