package com.serhii.tvmarketplace.features.addapp.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.serhii.tvmarketplace.core.ui.AppTheme
import com.serhii.tvmarketplace.core.ui.TvScreen
import com.serhii.tvmarketplace.core.strings.R as StringsR

@Composable
internal fun PreviewView(
  viewState: PreviewViewState,
  onAdd: () -> Unit,
  onOpen: () -> Unit,
  onCancel: () -> Unit,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val primaryFocus = remember { FocusRequester() }
  TvScreen(modifier) {
    when (viewState) {
      PreviewViewState.Loading -> {
        Text("…", style = MaterialTheme.typography.displaySmall)
        Actions {
          OutlinedButton(onClick = onCancel, modifier = Modifier.focusRequester(primaryFocus)) {
            Text(stringResource(StringsR.string.common_cancel))
          }
        }
      }
      is PreviewViewState.Content -> {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
          // the app's icon when the repository has one, the owner's avatar otherwise
          when {
            viewState.iconUrl != null -> AsyncImage(
              model = viewState.iconUrl,
              contentDescription = null,
              modifier = Modifier.size(96.dp).clip(RoundedCornerShape(20.dp)),
            )
            viewState.avatarUrl != null -> AsyncImage(
              model = viewState.avatarUrl,
              contentDescription = null,
              modifier = Modifier.size(96.dp).clip(CircleShape),
            )
          }
          Column {
            Text(viewState.name, style = MaterialTheme.typography.displaySmall)
            Text(viewState.ownerAndRepo, style = MaterialTheme.typography.titleMedium)
          }
        }
        Column(modifier = Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          if (viewState.alreadyTracked) {
            Text(stringResource(StringsR.string.preview_already_added), style = MaterialTheme.typography.titleMedium)
          }
          viewState.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
          Text(
            viewState.latestVersion?.let { stringResource(StringsR.string.preview_latest_version, it) }
              ?: stringResource(StringsR.string.preview_no_releases),
            style = MaterialTheme.typography.bodyLarge,
          )
          viewState.sender?.let { Text(stringResource(StringsR.string.preview_sent_from, it), style = MaterialTheme.typography.bodyMedium) }
        }
        Actions {
          if (viewState.alreadyTracked) {
            Button(onClick = onOpen, modifier = Modifier.focusRequester(primaryFocus)) {
              Text(stringResource(StringsR.string.preview_open))
            }
          } else {
            Button(onClick = onAdd, modifier = Modifier.focusRequester(primaryFocus)) { Text(stringResource(StringsR.string.preview_add)) }
          }
          OutlinedButton(onClick = onCancel) { Text(stringResource(StringsR.string.common_cancel)) }
        }
      }
      is PreviewViewState.Error -> {
        val message = when (viewState.kind) {
          PreviewViewState.ErrorKind.NotFound -> StringsR.string.preview_not_found
          PreviewViewState.ErrorKind.RateLimited -> StringsR.string.preview_rate_limited
          PreviewViewState.ErrorKind.Network -> StringsR.string.preview_network_error
          PreviewViewState.ErrorKind.LinkMissing -> StringsR.string.preview_link_missing
        }
        Text(stringResource(message), style = MaterialTheme.typography.headlineSmall)
        Actions {
          if (viewState.kind == PreviewViewState.ErrorKind.LinkMissing) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.focusRequester(primaryFocus)) {
              Text(stringResource(StringsR.string.common_cancel))
            }
          } else {
            Button(onClick = onRetry, modifier = Modifier.focusRequester(primaryFocus)) {
              Text(stringResource(StringsR.string.common_retry))
            }
            OutlinedButton(onClick = onCancel) { Text(stringResource(StringsR.string.common_cancel)) }
          }
        }
      }
      is PreviewViewState.Added -> Text(
        stringResource(StringsR.string.preview_added, viewState.name),
        style = MaterialTheme.typography.displaySmall,
      )
    }
  }
  LaunchedEffect(viewState::class, (viewState as? PreviewViewState.Content)?.alreadyTracked) {
    if (viewState !is PreviewViewState.Added) primaryFocus.requestFocus()
  }
}

@Composable
private fun Actions(content: @Composable () -> Unit) {
  Row(modifier = Modifier.padding(top = 32.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { content() }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun PreviewViewPreview() {
  AppTheme {
    PreviewView(
      viewState = PreviewViewState.Content(
        name = "Obtainium",
        ownerAndRepo = "ImranR98/Obtainium",
        description = "Get Android app updates straight from the source.",
        latestVersion = "v1.2.3",
        avatarUrl = null,
        sender = "Safari on iPhone",
        alreadyTracked = false,
      ),
      onAdd = {},
      onOpen = {},
      onCancel = {},
      onRetry = {},
    )
  }
}
