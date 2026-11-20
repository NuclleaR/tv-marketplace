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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.serhii.tvmarketplace.core.ui.AppIcon
import com.serhii.tvmarketplace.core.ui.AppTheme
import com.serhii.tvmarketplace.core.ui.PillButton
import com.serhii.tvmarketplace.core.ui.PreviewTv
import com.serhii.tvmarketplace.core.ui.TileCard
import com.serhii.tvmarketplace.core.ui.TvScreen
import com.serhii.tvmarketplace.core.ui.icons.AppIcons
import com.serhii.tvmarketplace.navigation.Destination
import com.serhii.tvmarketplace.navigation.DestinationSaver
import com.serhii.tvmarketplace.core.strings.R as StringsR

@Composable
internal fun HomeView(
  viewState: HomeViewState,
  onOpen: (Destination) -> Unit,
  modifier: Modifier = Modifier,
  onOpenNotice: (Long) -> Unit = {},
) {
  val screenFocus = remember { FocusRequester() }
  val openedFocus = remember { FocusRequester() }
  // where the user went from here: back on Home, focus returns to the button that opened it. A restorer's own
  // memory doesn't survive the screen leaving the composition, so the destination is saved and its button is
  // the restorer's fallback; the first time that is Add app
  var opened by rememberSaveable(stateSaver = DestinationSaver) { mutableStateOf<Destination>(Destination.AddApp) }
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
          items(viewState.apps, key = { it.id }) { app -> AppTile(app) }
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
private fun AppTile(app: HomeViewState.App) {
  // tapping an app does nothing yet: details and installs are the next feature
  TileCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
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
        Text(
          app.ownerAndRepo,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
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

private val previewApps = listOf(
  "Obtainium" to "ImranR98/Obtainium",
  "F-Droid" to "f-droid/fdroidclient",
  "SmartTube" to "yuliskov/SmartTube",
  "Kodi" to "xbmc/xbmc",
  "NewPipe" to "TeamNewPipe/NewPipe",
  "Jellyfin" to "jellyfin/jellyfin-androidtv",
).mapIndexed { index, (name, repo) -> HomeViewState.App(id = index.toLong(), name = name, ownerAndRepo = repo) }

@PreviewTv
@Composable
private fun HomeViewPreview() {
  AppTheme {
    HomeView(
      viewState = HomeViewState(apps = previewApps, inboxCount = 3, notice = HomeViewState.Notice(1, "Safari on iPhone")),
      onOpen = {},
    )
  }
}

@PreviewTv
@Composable
private fun HomeViewEmptyPreview() {
  AppTheme { HomeView(viewState = HomeViewState(apps = emptyList()), onOpen = {}) }
}
