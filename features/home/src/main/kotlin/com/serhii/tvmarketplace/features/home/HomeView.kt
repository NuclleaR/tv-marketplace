package com.serhii.tvmarketplace.features.home

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.serhii.tvmarketplace.core.domain.AppStatus
import com.serhii.tvmarketplace.core.domain.FailureReason
import com.serhii.tvmarketplace.core.ui.AppIcon
import com.serhii.tvmarketplace.core.ui.AppTheme
import com.serhii.tvmarketplace.core.ui.PillButton
import com.serhii.tvmarketplace.core.ui.PreviewTv
import com.serhii.tvmarketplace.core.ui.ProgressLine
import com.serhii.tvmarketplace.core.ui.StatusLine
import com.serhii.tvmarketplace.core.ui.TileCard
import com.serhii.tvmarketplace.core.ui.TvScreen
import com.serhii.tvmarketplace.core.ui.failureText
import com.serhii.tvmarketplace.core.ui.icons.AppIcons
import com.serhii.tvmarketplace.core.ui.relativeTime
import com.serhii.tvmarketplace.navigation.Destination
import com.serhii.tvmarketplace.navigation.DestinationSaver
import com.serhii.tvmarketplace.core.strings.R as StringsR

@Composable
internal fun HomeView(
  viewState: HomeViewState,
  onOpen: (Destination) -> Unit,
  modifier: Modifier = Modifier,
  onOpenNotice: (Long) -> Unit = {},
  onCheckForUpdates: () -> Unit = {},
  onUpdateAll: () -> Unit = {},
  onStopAfterCurrent: () -> Unit = {},
  onShowFailures: () -> Unit = {},
) {
  val screenFocus = remember { FocusRequester() }
  val openedFocus = remember { FocusRequester() }
  // where the user went from here: back on Home, focus returns to the button that opened it. A restorer's own
  // memory doesn't survive the screen leaving the composition, so the destination is saved and its button is
  // the restorer's fallback; the first time that is Add app
  var opened by rememberSaveable(stateSaver = DestinationSaver) { mutableStateOf<Destination>(Destination.AddApp) }
  // the app that was opened may have been removed there: then focus starts at Add app again
  val gone = opened.let { it is Destination.AppDetails && viewState.apps.none { app -> app.id == it.appId } }
  val failuresGone = opened == Destination.UpdateFailures && viewState.updates.batch?.failed.isNullOrEmpty()
  if (gone || failuresGone) opened = Destination.AddApp
  val open: (Destination) -> Unit = { destination ->
    opened = destination
    onOpen(destination)
  }

  // the one button that gets the requester is the one that opened the last screen
  fun Modifier.opener(destination: Destination) = if (destination == opened) focusRequester(openedFocus) else this
  // the notice never takes focus; Menu opens it from anywhere on the screen
  val menuOpensNotice = Modifier.onPreviewKeyEvent { event ->
    val notice = viewState.notice
    val handled = notice != null && event.key == Key.Menu && event.type == KeyEventType.KeyUp
    if (handled) onOpenNotice(checkNotNull(notice).linkId)
    handled
  }
  Box(modifier.fillMaxSize().then(menuOpensNotice).focusRequester(screenFocus).focusRestorer(openedFocus).focusGroup()) {
    TvScreen {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(AppIcons.Tv, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text(stringResource(StringsR.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
      }
      // each row is a focus group that returns to its item focused last, as TV launchers do;
      // the restorer goes before the group: focus properties apply to the next focus node in the chain
      Row(
        modifier = Modifier.padding(top = 32.dp, start = 12.dp).focusRestorer().focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        PillButton(
          text = stringResource(StringsR.string.home_add_app),
          onClick = { open(Destination.AddApp) },
          leadingIcon = AppIcons.Add,
          modifier = Modifier.opener(Destination.AddApp),
        )
        PillButton(
          text = stringResource(StringsR.string.home_inbox),
          onClick = { open(Destination.Inbox) },
          leadingIcon = AppIcons.Inbox,
          modifier = Modifier.opener(Destination.Inbox),
          trailing = if (viewState.inboxCount > 0) {
            { CountBadge(viewState.inboxCount) }
          } else {
            null
          },
        )
        PillButton(
          text = stringResource(StringsR.string.home_settings),
          onClick = { open(Destination.Settings) },
          leadingIcon = AppIcons.Settings,
          modifier = Modifier.opener(Destination.Settings),
        )
        PillButton(
          text = stringResource(if (viewState.updates.checking) StringsR.string.home_checking else StringsR.string.home_check),
          onClick = { if (!viewState.updates.checking) onCheckForUpdates() },
          leadingIcon = AppIcons.Refresh,
        )
      }
      if (viewState.updates.visible) {
        UpdatesRow(
          updates = viewState.updates,
          onUpdateAll = onUpdateAll,
          onStopAfterCurrent = onStopAfterCurrent,
          onShowFailures = {
            opened = Destination.UpdateFailures
            onShowFailures()
          },
          showFailuresFocus = if (opened == Destination.UpdateFailures) openedFocus else null,
          modifier = Modifier.padding(top = 24.dp, start = 12.dp),
        )
      } else {
        viewState.updates.lastCheckedAt?.let {
          Text(
            stringResource(StringsR.string.home_checked, relativeTime(it)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 20.dp, start = 12.dp),
          )
        }
      }
      Text(
        stringResource(StringsR.string.home_tracked_apps),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 40.dp),
      )
      if (viewState.apps.isEmpty()) {
        Text(
          stringResource(StringsR.string.home_empty),
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.padding(top = 16.dp),
        )
      } else {
        LazyVerticalGrid(
          modifier = Modifier.focusRestorer(),
          columns = GridCells.Fixed(3),
          contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 27.dp),
          horizontalArrangement = Arrangement.spacedBy(20.dp),
          verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
          items(viewState.apps, key = { it.id }) { app ->
            val destination = Destination.AppDetails(app.id)
            AppTile(app, onClick = { open(destination) }, modifier = Modifier.opener(destination))
          }
        }
      }
    }
    viewState.notice?.let { notice -> Notice(notice, Modifier.align(Alignment.BottomEnd).padding(horizontal = 48.dp, vertical = 27.dp)) }
  }
  // entering the screen goes through its restorer, which lands on the button that opened the last screen
  LaunchedEffect(Unit) { screenFocus.requestFocus() }
}

@Composable
private fun CountBadge(count: Int) {
  Surface(
    shape = CircleShape,
    colors = SurfaceDefaults.colors(
      containerColor = MaterialTheme.colorScheme.primaryContainer,
      contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ),
    modifier = Modifier.defaultMinSize(minWidth = 22.dp).height(22.dp),
  ) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
      Text(count.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
  }
}

@Composable
private fun UpdatesRow(
  updates: HomeViewState.Updates,
  onUpdateAll: () -> Unit,
  onStopAfterCurrent: () -> Unit,
  onShowFailures: () -> Unit,
  // set when Show opened the screen the user came back from
  showFailuresFocus: FocusRequester?,
  modifier: Modifier = Modifier,
) {
  val batch = updates.batch
  Row(
    modifier = modifier.focusRestorer().focusGroup(),
    horizontalArrangement = Arrangement.spacedBy(20.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    val summary = when {
      batch != null && batch.running -> stringResource(
        StringsR.string.home_updating,
        (batch.done + 1).coerceAtMost(batch.total),
        batch.total,
      )
      batch != null && batch.failed.isNotEmpty() -> stringResource(
        StringsR.string.home_update_result,
        batch.done - batch.failed.size,
        batch.failed.size,
      )
      updates.resumeAt != null -> failureText(FailureReason.RateLimited(updates.resumeAt))
      else -> pluralStringResource(StringsR.plurals.home_updates, updates.count, updates.count)
    }
    Text(summary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
    when {
      batch != null && batch.running && !batch.stopping ->
        PillButton(stringResource(StringsR.string.home_stop_after), onClick = onStopAfterCurrent)
      batch != null && batch.running -> Unit
      updates.count > 0 -> PillButton(
        stringResource(StringsR.string.home_update_all),
        onClick = onUpdateAll,
        leadingIcon = AppIcons.Refresh,
      )
    }
    if (batch != null && !batch.running && batch.failed.isNotEmpty()) {
      PillButton(
        stringResource(StringsR.string.home_show),
        onClick = onShowFailures,
        modifier = showFailuresFocus?.let { Modifier.focusRequester(it) } ?: Modifier,
      )
    }
  }
}

@Composable
private fun AppTile(
  app: HomeViewState.App,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  TileCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth().height(100.dp).padding(horizontal = 18.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      AppIcon(name = app.name, iconUrl = app.iconUrl, size = 64.dp)
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          app.name,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        StatusLine(app.status)
        (app.status as? AppStatus.Downloading)?.let { ProgressLine(it.fraction, Modifier.padding(top = 2.dp)) }
      }
    }
  }
}

@Composable
private fun Notice(
  notice: HomeViewState.Notice,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.width(330.dp),
    shape = RoundedCornerShape(14.dp),
    colors = SurfaceDefaults.colors(containerColor = AppTheme.surfaces.high),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        shape = CircleShape,
        colors = SurfaceDefaults.colors(
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.size(36.dp),
      ) {
        Box(contentAlignment = Alignment.Center) { Icon(AppIcons.Phone, contentDescription = null, modifier = Modifier.size(18.dp)) }
      }
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          stringResource(StringsR.string.home_notice, notice.sender),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Medium,
        )
        Text(
          stringResource(StringsR.string.home_notice_hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

private val previewStatuses = listOf(
  AppStatus.UpdateAvailable("1.7.3", "1.7.4"),
  AppStatus.UpToDate("1.21"),
  AppStatus.UpdateAvailable("32.79", "32.80s"),
  AppStatus.NoPackage(null),
  AppStatus.Downloading(0.6F),
  AppStatus.NotInstalled,
)

private val previewApps = listOf(
  "Obtainium" to "ImranR98/Obtainium",
  "F-Droid" to "f-droid/fdroidclient",
  "SmartTube" to "yuliskov/SmartTube",
  "Kodi" to "xbmc/xbmc",
  "NewPipe" to "TeamNewPipe/NewPipe",
  "Jellyfin" to "jellyfin/jellyfin-androidtv",
).mapIndexed { index, (name, repo) ->
  HomeViewState.App(id = index.toLong(), name = name, ownerAndRepo = repo, status = previewStatuses[index])
}

@PreviewTv
@Composable
private fun HomeViewPreview() {
  AppTheme {
    HomeView(
      viewState = HomeViewState(
        apps = previewApps,
        inboxCount = 3,
        notice = HomeViewState.Notice(1, "Safari on iPhone"),
        updates = HomeViewState.Updates(count = 2),
      ),
      onOpen = {},
    )
  }
}

@PreviewTv
@Composable
private fun HomeViewEmptyPreview() {
  AppTheme { HomeView(viewState = HomeViewState(apps = emptyList()), onOpen = {}) }
}
