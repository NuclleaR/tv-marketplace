package com.serhii.tvmarketplace.features.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.serhii.tvmarketplace.core.common.rootGraphAs
import com.serhii.tvmarketplace.core.data.IncomingLinksRepository
import com.serhii.tvmarketplace.core.data.TrackedAppsRepository
import com.serhii.tvmarketplace.core.domain.FDroidApp
import com.serhii.tvmarketplace.core.domain.GitHubRepoRef
import com.serhii.tvmarketplace.core.ui.AppIcon
import com.serhii.tvmarketplace.core.ui.AppTheme
import com.serhii.tvmarketplace.core.ui.ListRow
import com.serhii.tvmarketplace.core.ui.PillButton
import com.serhii.tvmarketplace.core.ui.PreviewTv
import com.serhii.tvmarketplace.core.ui.ScreenTitle
import com.serhii.tvmarketplace.core.ui.TvScreen
import com.serhii.tvmarketplace.core.ui.icons.AppIcons
import com.serhii.tvmarketplace.core.ui.relativeTime
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
        val tracked = apps.map { it.ref.key }.toSet()
        links.map { link ->
          InboxViewState.Item(
            id = link.id,
            name = when (val ref = link.ref) {
              is GitHubRepoRef -> ref.repo
              is FDroidApp -> ref.packageName.substringAfterLast('.')
            },
            ownerAndRepo = link.ref.label,
            sender = link.senderLabel,
            receivedAt = link.receivedAt,
            alreadyTracked = link.ref.key in tracked,
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
  val listFocus = remember { FocusRequester() }
  val openedFocus = remember { FocusRequester() }
  // the link that opened a preview: back here, focus returns to it. A restorer's own memory doesn't survive
  // the screen leaving the composition, so the link is saved here and given to it as the fallback
  var opened by rememberSaveable { mutableStateOf<Long?>(null) }
  val openedShown = viewState.items.any { it.id == opened }
  TvScreen(modifier) {
    if (viewState.items.isEmpty()) {
      ScreenTitle(stringResource(StringsR.string.inbox_title))
      Column(
        modifier = Modifier.fillMaxSize().padding(bottom = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(AppTheme.surfaces.low), contentAlignment = Alignment.Center) {
          Icon(
            AppIcons.Inbox,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
          )
        }
        Text(
          stringResource(StringsR.string.inbox_empty),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Medium,
          modifier = Modifier.padding(top = 24.dp),
        )
        PillButton(
          text = stringResource(StringsR.string.home_add_app),
          onClick = onAddApp,
          leadingIcon = AppIcons.Add,
          modifier = Modifier.padding(top = 36.dp).focusRequester(firstFocus),
        )
      }
    } else {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
        ScreenTitle(stringResource(StringsR.string.inbox_title))
        Text(
          stringResource(StringsR.string.inbox_dismiss_hint),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(bottom = 4.dp),
        )
      }
      LazyColumn(
        // a link that was added or dismissed meanwhile is gone, then focus starts at the top
        modifier = Modifier.fillMaxWidth().focusRequester(listFocus).focusRestorer(if (openedShown) openedFocus else firstFocus),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(top = 32.dp, bottom = 27.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        items(viewState.items, key = { it.id }) { item ->
          val first = if (item == viewState.items.first()) Modifier.focusRequester(firstFocus) else Modifier
          val focus = if (item.id == opened) first.focusRequester(openedFocus) else first
          InboxRow(
            item = item,
            onOpen = {
              opened = item.id
              onOpen(item.id)
            },
            onDismiss = { onDismiss(item.id) },
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
  // entering the list goes through its restorer: the first link, or the one a preview was opened from
  LaunchedEffect(viewState.items.isEmpty()) {
    if (viewState.items.isEmpty()) firstFocus.requestFocus() else listFocus.requestFocus()
  }
}

@Composable
private fun InboxRow(
  item: InboxViewState.Item,
  onOpen: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  ListRow(
    onClick = onOpen,
    onLongClick = onDismiss,
    modifier = modifier.width(700.dp),
    leading = { AppIcon(name = item.name, iconUrl = null, size = 52.dp) },
    headline = { Text(item.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    supporting = { Text(item.ownerAndRepo, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    trailing = {
      Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (item.alreadyTracked) {
          Row(
            modifier = Modifier.clip(
              CircleShape,
            ).background(MaterialTheme.colorScheme.secondaryContainer).padding(start = 7.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              AppIcons.Check,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.size(12.dp),
            )
            Text(
              stringResource(StringsR.string.preview_already_added),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          }
        }
        Text(
          stringResource(StringsR.string.inbox_sender, item.sender, relativeTime(item.receivedAt)),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
  )
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

@PreviewTv
@Composable
private fun InboxViewEmptyPreview() {
  AppTheme { InboxView(viewState = InboxViewState(emptyList()), onOpen = {}, onDismiss = {}, onAddApp = {}) }
}

@PreviewTv
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
