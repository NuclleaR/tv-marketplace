package com.serhii.tvmarketplace.features.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.serhii.tvmarketplace.core.ui.AppTheme
import com.serhii.tvmarketplace.core.ui.TvScreen
import com.serhii.tvmarketplace.core.strings.R as StringsR

@Composable
internal fun HomeView(
  viewState: HomeViewState,
  onAddApp: () -> Unit,
  modifier: Modifier = Modifier,
  onInbox: () -> Unit = {},
  onSettings: () -> Unit = {},
  onOpenNotice: (Long) -> Unit = {},
) {
  val addFocus = remember { FocusRequester() }
  // the notice never takes focus; Menu opens it from anywhere on the screen
  val menuOpensNotice = Modifier.onPreviewKeyEvent { event ->
    val notice = viewState.notice
    val handled = notice != null && event.key == Key.Menu && event.type == KeyEventType.KeyUp
    if (handled) onOpenNotice(checkNotNull(notice).linkId)
    handled
  }
  Box(modifier.fillMaxSize().then(menuOpensNotice)) {
    TvScreen {
      Text(stringResource(StringsR.string.app_name), style = MaterialTheme.typography.displaySmall)
      Row(modifier = Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(onClick = onAddApp, modifier = Modifier.focusRequester(addFocus)) {
          Text(stringResource(StringsR.string.home_add_app))
        }
        OutlinedButton(onClick = onInbox) { Text(stringResource(StringsR.string.home_inbox, viewState.inboxCount)) }
        OutlinedButton(onClick = onSettings) { Text(stringResource(StringsR.string.home_settings)) }
      }
      Text(
        stringResource(StringsR.string.home_tracked_apps),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 32.dp),
      )
      if (viewState.apps.isEmpty()) {
        Text(stringResource(StringsR.string.home_empty), modifier = Modifier.padding(top = 16.dp))
      } else {
        LazyColumn(contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          items(viewState.apps, key = { it.id }) { app ->
            ListItem(
              selected = false,
              onClick = {},
              headlineContent = { Text(app.name) },
              supportingContent = { Text(app.ownerAndRepo) },
            )
          }
        }
      }
    }
    viewState.notice?.let { notice ->
      Surface(
        modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal = 48.dp, vertical = 27.dp),
        colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
      ) {
        Text(stringResource(StringsR.string.home_notice, notice.sender), modifier = Modifier.padding(16.dp))
      }
    }
  }
  LaunchedEffect(Unit) { addFocus.requestFocus() }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun HomeViewPreview() {
  AppTheme {
    HomeView(
      viewState = HomeViewState(apps = List(4) { HomeViewState.App(id = it.toLong(), name = "App $it", ownerAndRepo = "owner/app$it") }),
      onAddApp = {},
    )
  }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun HomeViewNoticePreview() {
  AppTheme {
    HomeView(
      viewState = HomeViewState(apps = emptyList(), inboxCount = 1, notice = HomeViewState.Notice(1, "Safari on iPhone")),
      onAddApp = {},
    )
  }
}
