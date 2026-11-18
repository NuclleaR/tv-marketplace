package com.serhii.tvmarketplace.features.inbox

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.serhii.tvmarketplace.core.common.rootGraphAs
import com.serhii.tvmarketplace.core.data.IncomingLinksRepository
import com.serhii.tvmarketplace.core.data.TrackedAppsRepository
import com.serhii.tvmarketplace.core.ui.AppTheme
import com.serhii.tvmarketplace.core.ui.TvScreen
import com.serhii.tvmarketplace.navigation.Destination
import com.serhii.tvmarketplace.navigation.NavEntryProvider
import com.serhii.tvmarketplace.navigation.Navigator
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import com.serhii.tvmarketplace.core.strings.R as StringsR

data class InboxViewState(val items: List<Item>) {
  data class Item(
    val id: Long,
    val name: String,
    val ownerAndRepo: String,
    val sender: String,
    val receivedAt: Instant,
    val alreadyTracked: Boolean,
  )
}

@Inject
class InboxViewModel(
  private val incomingLinks: IncomingLinksRepository,
  private val trackedApps: TrackedAppsRepository,
  private val navigator: Navigator,
  private val scope: CoroutineScope,
) {

  @Composable
  fun viewState(): InboxViewState {
    val items by remember {
      combine(incomingLinks.pending(), trackedApps.trackedApps()) { links, apps ->
        // derived when shown, so a link stays correct if its app is added or removed later
        val tracked = apps.map { it.ref.owner.lowercase() to it.ref.repo.lowercase() }.toSet()
        links.map { link ->
          InboxViewState.Item(
            id = link.id,
            name = link.ref.repo,
            ownerAndRepo = "${link.ref.owner}/${link.ref.repo}",
            sender = link.senderLabel,
            receivedAt = link.receivedAt,
            alreadyTracked = (link.ref.owner.lowercase() to link.ref.repo.lowercase()) in tracked,
          )
        }
      }
    }.collectAsState(initial = emptyList())
    return InboxViewState(items)
  }

  fun onOpen(id: Long) {
    navigator.goTo(Destination.Preview(linkId = id))
  }

  fun onDismiss(id: Long) {
    val _ = scope.launch { incomingLinks.markDismissed(id) }
  }

  fun onAddApp() {
    navigator.goTo(Destination.AddApp)
  }
}

@Composable
internal fun InboxView(
  viewState: InboxViewState,
  onOpen: (Long) -> Unit,
  onDismiss: (Long) -> Unit,
  onAddApp: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val firstFocus = remember { FocusRequester() }
  TvScreen(modifier) {
    Text(stringResource(StringsR.string.inbox_title), style = MaterialTheme.typography.displaySmall)
    if (viewState.items.isEmpty()) {
      Text(stringResource(StringsR.string.inbox_empty), modifier = Modifier.padding(top = 24.dp))
      Button(onClick = onAddApp, modifier = Modifier.padding(top = 16.dp).focusRequester(firstFocus)) {
        Text(stringResource(StringsR.string.home_add_app))
      }
    } else {
      Text(stringResource(StringsR.string.inbox_dismiss_hint), style = MaterialTheme.typography.bodyMedium)
      LazyColumn(contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(viewState.items, key = { it.id }) { item ->
          val focus = if (item == viewState.items.first()) Modifier.focusRequester(firstFocus) else Modifier
          ListItem(
            selected = false,
            onClick = { onOpen(item.id) },
            onLongClick = { onDismiss(item.id) },
            headlineContent = { Text(item.name) },
            supportingContent = {
              val time = DateUtils.getRelativeTimeSpanString(item.receivedAt.toEpochMilli()).toString()
              Text("${item.ownerAndRepo} · ${stringResource(StringsR.string.inbox_sender, item.sender, time)}")
            },
            trailingContent = if (item.alreadyTracked) {
              { Text(stringResource(StringsR.string.preview_already_added)) }
            } else {
              null
            },
            modifier = focus.onPreviewKeyEvent { event ->
              val menu = event.key == Key.Menu && event.type == KeyEventType.KeyUp
              if (menu) onDismiss(item.id)
              menu
            },
          )
        }
      }
    }
  }
  LaunchedEffect(viewState.items.isEmpty()) { firstFocus.requestFocus() }
}

@ContributesTo(AppScope::class)
interface InboxGraph {
  val inboxViewModel: InboxViewModel
}

@BindingContainer
@ContributesTo(AppScope::class)
object InboxProvider {

  @Provides
  @IntoSet
  fun inboxNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.Inbox> { key ->
    NavEntry(key) { InboxScreen() }
  }
}

@Composable
fun InboxScreen() {
  val viewModel = retain { rootGraphAs<InboxGraph>().inboxViewModel }
  InboxView(viewState = viewModel.viewState(), onOpen = viewModel::onOpen, onDismiss = viewModel::onDismiss, onAddApp = viewModel::onAddApp)
}

@Preview(device = "id:tv_1080p")
@Composable
private fun InboxViewPreview() {
  AppTheme {
    InboxView(
      viewState = InboxViewState(
        items = listOf(
          InboxViewState.Item(1, "Obtainium", "ImranR98/Obtainium", "Safari on iPhone", Instant.now(), alreadyTracked = false),
          InboxViewState.Item(2, "NewPipe", "TeamNewPipe/NewPipe", "Chrome on Android", Instant.now(), alreadyTracked = true),
        ),
      ),
      onOpen = {},
      onDismiss = {},
      onAddApp = {},
    )
  }
}
