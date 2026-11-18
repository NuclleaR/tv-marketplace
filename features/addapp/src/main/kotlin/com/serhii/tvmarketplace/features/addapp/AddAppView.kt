package com.serhii.tvmarketplace.features.addapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.serhii.tvmarketplace.core.ui.AppTheme
import com.serhii.tvmarketplace.core.ui.PillButton
import com.serhii.tvmarketplace.core.ui.PreviewTv
import com.serhii.tvmarketplace.core.ui.ScreenTitle
import com.serhii.tvmarketplace.core.ui.TvScreen
import com.serhii.tvmarketplace.core.ui.icons.AppIcons
import com.serhii.tvmarketplace.core.strings.R as StringsR

private val panelSize = 280.dp

@Composable
internal fun AddAppView(
  viewState: AddAppViewState,
  onTypeIt: () -> Unit,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
  onNetworkSettings: () -> Unit = {},
) {
  val primaryFocus = remember { FocusRequester() }
  TvScreen(modifier) {
    ScreenTitle(stringResource(StringsR.string.add_app_title))
    Row(
      modifier = Modifier.fillMaxSize().padding(start = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(56.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      when (viewState) {
        is AddAppViewState.Ready -> {
          // a light panel around the code: phone cameras read dark modules on white best
          Box(Modifier.size(panelSize).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(22.dp)) {
            QrCode(
              content = viewState.qrContent,
              contentDescription = stringResource(StringsR.string.add_app_qr_description),
              size = panelSize - 44.dp,
            )
          }
          Ready(viewState, onTypeIt, primaryFocus)
        }
        AddAppViewState.NoNetwork -> {
          IconPanel()
          Column {
            Text(stringResource(StringsR.string.add_app_no_network), style = MaterialTheme.typography.headlineMedium)
            Text(
              stringResource(StringsR.string.add_app_no_network_body),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(top = 12.dp).widthIn(max = 450.dp),
            )
            Row(modifier = Modifier.padding(top = 36.dp, start = 10.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
              PillButton(
                text = stringResource(StringsR.string.common_retry),
                onClick = onRetry,
                leadingIcon = AppIcons.Refresh,
                modifier = Modifier.focusRequester(primaryFocus),
              )
              PillButton(text = stringResource(StringsR.string.add_app_network_settings), onClick = onNetworkSettings)
            }
          }
        }
        AddAppViewState.Failed -> {
          IconPanel()
          Column {
            Text(stringResource(StringsR.string.add_app_failed), style = MaterialTheme.typography.headlineMedium)
            Row(modifier = Modifier.padding(top = 36.dp, start = 10.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
              PillButton(
                text = stringResource(StringsR.string.common_retry),
                onClick = onRetry,
                leadingIcon = AppIcons.Refresh,
                modifier = Modifier.focusRequester(primaryFocus),
              )
              PillButton(text = stringResource(StringsR.string.add_app_type_it), onClick = onTypeIt, leadingIcon = AppIcons.Keyboard)
            }
          }
        }
        AddAppViewState.Loading -> Unit
      }
    }
  }
  LaunchedEffect(viewState::class) {
    if (viewState != AddAppViewState.Loading) primaryFocus.requestFocus()
  }
}

@Composable
private fun Ready(
  viewState: AddAppViewState.Ready,
  onTypeIt: () -> Unit,
  typeItFocus: FocusRequester,
) {
  Column {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
      Step(1, stringResource(StringsR.string.add_app_step_scan))
      Step(2, stringResource(StringsR.string.add_app_step_send))
    }
    Box(Modifier.padding(top = 32.dp).fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.borderVariant))
    Text(
      stringResource(StringsR.string.add_app_manual, viewState.address),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 24.dp),
    )
    Text(
      viewState.pin,
      style = MaterialTheme.typography.displaySmall,
      fontWeight = FontWeight.Bold,
      letterSpacing = 3.sp,
      modifier = Modifier.padding(top = 6.dp),
    )
    Row(
      modifier = Modifier.padding(top = 20.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(AppIcons.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
      Text(
        stringResource(StringsR.string.add_app_same_wifi),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    PillButton(
      text = stringResource(StringsR.string.add_app_type_it),
      onClick = onTypeIt,
      leadingIcon = AppIcons.Keyboard,
      modifier = Modifier.padding(top = 28.dp, start = 10.dp).focusRequester(typeItFocus),
    )
  }
}

@Composable
private fun Step(
  number: Int,
  text: String,
) {
  Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(
      Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        number.toString(),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
      )
    }
    Text(text, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal))
  }
}

@Composable
private fun IconPanel() {
  Box(
    Modifier.size(panelSize).clip(RoundedCornerShape(18.dp)).background(AppTheme.surfaces.low),
    contentAlignment = Alignment.Center,
  ) {
    Icon(AppIcons.WifiOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(88.dp))
  }
}

@PreviewTv
@Composable
private fun AddAppViewPreview() {
  AppTheme {
    AddAppView(
      viewState = AddAppViewState.Ready("http://192.168.1.42:8765/s/abcdefghijklmnop", "http://192.168.1.42:8765", "482 913"),
      onTypeIt = {},
      onRetry = {},
    )
  }
}

@PreviewTv
@Composable
private fun AddAppViewNoNetworkPreview() {
  AppTheme { AddAppView(viewState = AddAppViewState.NoNetwork, onTypeIt = {}, onRetry = {}) }
}
