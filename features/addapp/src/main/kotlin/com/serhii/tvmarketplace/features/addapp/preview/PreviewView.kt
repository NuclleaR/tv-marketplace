package com.serhii.tvmarketplace.features.addapp.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.serhii.tvmarketplace.core.ui.AppIcon
import com.serhii.tvmarketplace.core.ui.AppTheme
import com.serhii.tvmarketplace.core.ui.PillButton
import com.serhii.tvmarketplace.core.ui.PreviewTv
import com.serhii.tvmarketplace.core.ui.TvScreen
import com.serhii.tvmarketplace.core.ui.icons.AppIcons
import com.serhii.tvmarketplace.core.strings.R as StringsR

private val heroSize = 144.dp

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
    Text(
      stringResource(StringsR.string.add_app_title),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
      modifier = Modifier.fillMaxSize().padding(start = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(44.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      when (viewState) {
        PreviewViewState.Loading -> Loading(onCancel, primaryFocus)
        is PreviewViewState.Content -> Content(viewState, onAdd, onOpen, onCancel, primaryFocus)
        is PreviewViewState.Error -> Error(viewState.kind, onRetry, onCancel, primaryFocus)
        is PreviewViewState.Added -> {
          HeroPanel(AppIcons.Check, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
          Text(stringResource(StringsR.string.preview_added, viewState.name), style = MaterialTheme.typography.displaySmall)
        }
      }
    }
  }
  LaunchedEffect(viewState::class, (viewState as? PreviewViewState.Content)?.alreadyTracked) {
    if (viewState !is PreviewViewState.Added) primaryFocus.requestFocus()
  }
}

@Composable
private fun RowScope.Content(
  viewState: PreviewViewState.Content,
  onAdd: () -> Unit,
  onOpen: () -> Unit,
  onCancel: () -> Unit,
  primaryFocus: FocusRequester,
) {
  // the app's own icon is the hero; the owner's avatar sits next to their name
  AppIcon(name = viewState.name, iconUrl = viewState.iconUrl, avatarUrl = null, size = heroSize)
  Column(Modifier.weight(1F)) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(
        viewState.name,
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1F, fill = false),
      )
      if (viewState.alreadyTracked) AlreadyAddedBadge()
    }
    Row(
      modifier = Modifier.padding(top = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(9.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OwnerAvatar(viewState.ownerAndRepo, viewState.avatarUrl, 28.dp)
      Text(viewState.ownerAndRepo, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    viewState.description?.let {
      Text(
        it,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 18.dp).widthIn(max = 560.dp),
      )
    }
    Row(modifier = Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Chip(
        viewState.latestVersion?.let { stringResource(StringsR.string.preview_latest_version, it) }
          ?: stringResource(StringsR.string.preview_no_releases),
      )
      viewState.sender?.let { Chip(stringResource(StringsR.string.preview_sent_from, it), icon = AppIcons.Phone) }
    }
    Actions {
      if (viewState.alreadyTracked) {
        PillButton(
          stringResource(StringsR.string.preview_open),
          onClick = onOpen,
          leadingIcon = AppIcons.ArrowForward,
          modifier = Modifier.focusRequester(primaryFocus),
        )
      } else {
        PillButton(
          stringResource(StringsR.string.preview_add),
          onClick = onAdd,
          leadingIcon = AppIcons.Add,
          modifier = Modifier.focusRequester(primaryFocus),
        )
      }
      PillButton(stringResource(StringsR.string.common_cancel), onClick = onCancel)
    }
  }
}

@Composable
private fun RowScope.Loading(
  onCancel: () -> Unit,
  primaryFocus: FocusRequester,
) {
  Box(Modifier.size(heroSize).clip(RoundedCornerShape(heroSize / 4)).background(AppTheme.surfaces.mid))
  Column(Modifier.weight(1F)) {
    Placeholder(310.dp, 36.dp, AppTheme.surfaces.mid)
    Row(
      modifier = Modifier.padding(top = 14.dp),
      horizontalArrangement = Arrangement.spacedBy(9.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(Modifier.size(28.dp).clip(CircleShape).background(AppTheme.surfaces.mid))
      Placeholder(170.dp, 17.dp, AppTheme.surfaces.mid)
    }
    Column(modifier = Modifier.padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
      Placeholder(540.dp, 15.dp, AppTheme.surfaces.low)
      Placeholder(500.dp, 15.dp, AppTheme.surfaces.low)
      Placeholder(280.dp, 15.dp, AppTheme.surfaces.low)
    }
    Text(
      stringResource(StringsR.string.preview_loading),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 22.dp),
    )
    Actions {
      PillButton(stringResource(StringsR.string.common_cancel), onClick = onCancel, modifier = Modifier.focusRequester(primaryFocus))
    }
  }
}

@Composable
private fun RowScope.Error(
  kind: PreviewViewState.ErrorKind,
  onRetry: () -> Unit,
  onCancel: () -> Unit,
  primaryFocus: FocusRequester,
) {
  val message = when (kind) {
    PreviewViewState.ErrorKind.NotFound -> StringsR.string.preview_not_found
    PreviewViewState.ErrorKind.RateLimited -> StringsR.string.preview_rate_limited
    PreviewViewState.ErrorKind.Network -> StringsR.string.preview_network_error
    PreviewViewState.ErrorKind.LinkMissing -> StringsR.string.preview_link_missing
  }
  HeroPanel(AppIcons.Alert, AppTheme.surfaces.low, MaterialTheme.colorScheme.error)
  Column(Modifier.weight(1F)) {
    Text(stringResource(message), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.widthIn(max = 560.dp))
    Actions {
      // a missing link can't be retried, only left
      if (kind == PreviewViewState.ErrorKind.LinkMissing) {
        PillButton(stringResource(StringsR.string.common_cancel), onClick = onCancel, modifier = Modifier.focusRequester(primaryFocus))
      } else {
        PillButton(
          stringResource(StringsR.string.common_retry),
          onClick = onRetry,
          leadingIcon = AppIcons.Refresh,
          modifier = Modifier.focusRequester(primaryFocus),
        )
        PillButton(stringResource(StringsR.string.common_cancel), onClick = onCancel)
      }
    }
  }
}

@Composable
private fun HeroPanel(
  icon: ImageVector,
  container: Color,
  content: Color,
) {
  Box(Modifier.size(heroSize).clip(RoundedCornerShape(heroSize / 4)).background(container), contentAlignment = Alignment.Center) {
    Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(64.dp))
  }
}

@Composable
private fun OwnerAvatar(
  ownerAndRepo: String,
  avatarUrl: String?,
  size: Dp,
) {
  if (avatarUrl != null) {
    AsyncImage(model = avatarUrl, contentDescription = null, modifier = Modifier.size(size).clip(CircleShape))
  } else {
    Box(Modifier.size(size).clip(CircleShape).background(AppTheme.surfaces.high), contentAlignment = Alignment.Center) {
      Text(
        ownerAndRepo.take(1).uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun AlreadyAddedBadge() {
  Row(
    modifier = Modifier
      .clip(CircleShape)
      .background(MaterialTheme.colorScheme.secondaryContainer)
      .padding(start = 8.dp, end = 11.dp, top = 5.dp, bottom = 5.dp),
    horizontalArrangement = Arrangement.spacedBy(5.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(AppIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(14.dp))
    Text(
      stringResource(StringsR.string.preview_already_added),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
  }
}

@Composable
private fun Chip(
  text: String,
  icon: ImageVector = AppIcons.Tag,
) {
  Row(
    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(AppTheme.surfaces.low).padding(horizontal = 12.dp, vertical = 7.dp),
    horizontalArrangement = Arrangement.spacedBy(7.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
    Text(text, style = MaterialTheme.typography.bodyLarge)
  }
}

@Composable
private fun Placeholder(
  width: Dp,
  height: Dp,
  color: Color,
) {
  Box(Modifier.width(width).height(height).clip(RoundedCornerShape(6.dp)).background(color))
}

@Composable
private fun Actions(content: @Composable RowScope.() -> Unit) {
  Row(modifier = Modifier.padding(top = 36.dp, start = 10.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), content = content)
}

private val previewContent = PreviewViewState.Content(
  name = "Obtainium",
  ownerAndRepo = "ImranR98/Obtainium",
  description = "Get Android app updates straight from the source.",
  latestVersion = "v1.7.4",
  avatarUrl = null,
  sender = "Safari on iPhone",
  alreadyTracked = false,
)

@PreviewTv
@Composable
private fun PreviewViewPreview() {
  AppTheme { PreviewView(viewState = previewContent, onAdd = {}, onOpen = {}, onCancel = {}, onRetry = {}) }
}

@PreviewTv
@Composable
private fun PreviewViewAddedPreview() {
  AppTheme {
    PreviewView(
      viewState = previewContent.copy(alreadyTracked = true, latestVersion = null, sender = null),
      onAdd = {},
      onOpen = {},
      onCancel = {},
      onRetry = {},
    )
  }
}

@PreviewTv
@Composable
private fun PreviewViewLoadingPreview() {
  AppTheme { PreviewView(viewState = PreviewViewState.Loading, onAdd = {}, onOpen = {}, onCancel = {}, onRetry = {}) }
}

@PreviewTv
@Composable
private fun PreviewViewErrorPreview() {
  AppTheme {
    PreviewView(
      viewState = PreviewViewState.Error(PreviewViewState.ErrorKind.Network),
      onAdd = {},
      onOpen = {},
      onCancel = {},
      onRetry = {},
    )
  }
}
