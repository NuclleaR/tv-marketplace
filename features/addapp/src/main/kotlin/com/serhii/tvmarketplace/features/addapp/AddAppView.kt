package com.serhii.tvmarketplace.features.addapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.serhii.tvmarketplace.core.ui.AppTheme
import com.serhii.tvmarketplace.core.ui.TvScreen
import com.serhii.tvmarketplace.core.strings.R as StringsR

@Composable
internal fun AddAppView(
  viewState: AddAppViewState,
  onTypeIt: () -> Unit,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val primaryFocus = remember { FocusRequester() }
  TvScreen(modifier) {
    Text(stringResource(StringsR.string.add_app_title), style = MaterialTheme.typography.displaySmall)
    Row(
      modifier = Modifier.fillMaxSize().padding(top = 32.dp),
      horizontalArrangement = Arrangement.spacedBy(48.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      when (viewState) {
        is AddAppViewState.Ready -> QrCode(
          content = viewState.qrContent,
          contentDescription = stringResource(StringsR.string.add_app_qr_description),
          size = 280.dp,
        )
        else -> Unit
      }
      Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (viewState) {
          is AddAppViewState.Ready -> {
            Text(stringResource(StringsR.string.add_app_step_scan), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(StringsR.string.add_app_step_send), style = MaterialTheme.typography.headlineSmall)
            Text(
              stringResource(StringsR.string.add_app_manual, viewState.address, viewState.pin),
              style = MaterialTheme.typography.titleMedium,
            )
            Text(stringResource(StringsR.string.add_app_same_wifi), style = MaterialTheme.typography.bodyLarge)
          }
          AddAppViewState.NoNetwork, AddAppViewState.Failed -> {
            val message = if (viewState == AddAppViewState.NoNetwork) StringsR.string.add_app_no_network else StringsR.string.add_app_failed
            Text(stringResource(message), style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onRetry, modifier = Modifier.focusRequester(primaryFocus)) {
              Text(stringResource(StringsR.string.common_retry))
            }
          }
          AddAppViewState.Loading -> Unit
        }
        val typeItModifier = if (viewState is AddAppViewState.NoNetwork || viewState is AddAppViewState.Failed) {
          Modifier
        } else {
          Modifier.focusRequester(primaryFocus)
        }
        Button(onClick = onTypeIt, modifier = typeItModifier.padding(top = 16.dp)) {
          Text(stringResource(StringsR.string.add_app_type_it))
        }
      }
    }
  }
  LaunchedEffect(viewState::class) { primaryFocus.requestFocus() }
}

@Preview(device = "id:tv_1080p")
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

@Preview(device = "id:tv_1080p")
@Composable
private fun AddAppViewNoNetworkPreview() {
  AppTheme { AddAppView(viewState = AddAppViewState.NoNetwork, onTypeIt = {}, onRetry = {}) }
}
