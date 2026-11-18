package com.serhii.tvmarketplace.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation3.runtime.NavEntry
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.serhii.tvmarketplace.core.common.rootGraphAs
import com.serhii.tvmarketplace.core.data.LinkShareSettings
import com.serhii.tvmarketplace.core.data.PairedPhoneKind
import com.serhii.tvmarketplace.core.data.PairedPhonesRepository
import com.serhii.tvmarketplace.core.ui.AppTheme
import com.serhii.tvmarketplace.core.ui.TvScreen
import com.serhii.tvmarketplace.core.ui.relativeTime
import com.serhii.tvmarketplace.navigation.Destination
import com.serhii.tvmarketplace.navigation.NavEntryProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import com.serhii.tvmarketplace.core.strings.R as StringsR

data class SettingsViewState(
  val phones: List<Phone>,
  val receiveInBackground: Boolean,
  val confirmRemoval: Phone?,
) {
  data class Phone(
    val id: String,
    val name: String,
    val kind: PairedPhoneKind,
    val lastUsedAt: Instant,
  )
}

@Inject
class SettingsViewModel(
  private val phones: PairedPhonesRepository,
  private val settings: LinkShareSettings,
  private val scope: CoroutineScope,
) {

  private var confirmRemovalId by mutableStateOf<String?>(null)

  @Composable
  fun viewState(): SettingsViewState {
    val paired by remember { phones.phones() }.collectAsState(initial = emptyList())
    val background by remember { settings.receiveInBackground }.collectAsState(initial = true)
    val items = paired.map { SettingsViewState.Phone(id = it.id, name = it.displayName, kind = it.kind, lastUsedAt = it.lastUsedAt) }
    return SettingsViewState(
      phones = items,
      receiveInBackground = background,
      confirmRemoval = items.firstOrNull {
        it.id ==
          confirmRemovalId
      },
    )
  }

  fun onPhoneClick(id: String) {
    confirmRemovalId = id
  }

  fun onConfirmRemoval() {
    val id = confirmRemovalId ?: return
    confirmRemovalId = null
    val _ = scope.launch { phones.remove(id) }
  }

  fun onCancelRemoval() {
    confirmRemovalId = null
  }

  fun onToggleBackground() {
    val _ = scope.launch { settings.setReceiveInBackground(!settings.receiveInBackground.first()) }
  }
}

@Composable
internal fun SettingsView(
  viewState: SettingsViewState,
  onPhoneClick: (String) -> Unit,
  onConfirmRemoval: () -> Unit,
  onCancelRemoval: () -> Unit,
  onToggleBackground: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val firstFocus = remember { FocusRequester() }
  TvScreen(modifier) {
    Text(stringResource(StringsR.string.settings_title), style = MaterialTheme.typography.displaySmall)
    LazyColumn(contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      item {
        ListItem(
          selected = false,
          onClick = onToggleBackground,
          headlineContent = { Text(stringResource(StringsR.string.settings_background)) },
          supportingContent = { Text(stringResource(StringsR.string.settings_background_summary)) },
          trailingContent = {
            Text(stringResource(if (viewState.receiveInBackground) StringsR.string.settings_on else StringsR.string.settings_off))
          },
          modifier = Modifier.focusRequester(firstFocus),
        )
      }
      item {
        Text(
          stringResource(StringsR.string.settings_phones),
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.padding(top = 24.dp),
        )
      }
      if (viewState.phones.isEmpty()) {
        item { Text(stringResource(StringsR.string.settings_no_phones)) }
      }
      items(viewState.phones, key = { it.id }) { phone ->
        ListItem(
          selected = false,
          onClick = { onPhoneClick(phone.id) },
          headlineContent = { Text(phone.name) },
          supportingContent = {
            val time = relativeTime(phone.lastUsedAt)
            Text(stringResource(StringsR.string.settings_last_used, time))
          },
        )
      }
      item {
        Text(
          stringResource(StringsR.string.settings_ip_tip),
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.padding(top = 24.dp),
        )
      }
    }
  }
  LaunchedEffect(Unit) { firstFocus.requestFocus() }
  viewState.confirmRemoval?.let { phone -> RemoveDialog(phone.name, onConfirmRemoval, onCancelRemoval) }
}

@Composable
private fun RemoveDialog(
  name: String,
  onConfirm: () -> Unit,
  onCancel: () -> Unit,
) {
  val cancelFocus = remember { FocusRequester() }
  Dialog(onDismissRequest = onCancel) {
    Surface {
      Column(modifier = Modifier.width(480.dp).padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(StringsR.string.settings_remove_title, name), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(StringsR.string.settings_remove_body))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          // the safe choice is the default one
          Button(onClick = onCancel, modifier = Modifier.focusRequester(cancelFocus)) {
            Text(stringResource(StringsR.string.common_cancel))
          }
          OutlinedButton(onClick = onConfirm) { Text(stringResource(StringsR.string.settings_remove)) }
        }
      }
    }
  }
  LaunchedEffect(Unit) { cancelFocus.requestFocus() }
}

@ContributesTo(AppScope::class)
interface SettingsGraph {
  val settingsViewModel: SettingsViewModel
}

@BindingContainer
@ContributesTo(AppScope::class)
object SettingsProvider {

  @Provides
  @IntoSet
  fun settingsNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.Settings> { key ->
    NavEntry(key) { SettingsScreen() }
  }
}

@Composable
fun SettingsScreen() {
  val viewModel = retain { rootGraphAs<SettingsGraph>().settingsViewModel }
  SettingsView(
    viewState = viewModel.viewState(),
    onPhoneClick = viewModel::onPhoneClick,
    onConfirmRemoval = viewModel::onConfirmRemoval,
    onCancelRemoval = viewModel::onCancelRemoval,
    onToggleBackground = viewModel::onToggleBackground,
  )
}

@Preview(device = "id:tv_1080p")
@Composable
private fun SettingsViewPreview() {
  AppTheme {
    SettingsView(
      viewState = SettingsViewState(
        phones = listOf(SettingsViewState.Phone("1", "Safari on iPhone", PairedPhoneKind.BROWSER, Instant.now())),
        receiveInBackground = true,
        confirmRemoval = null,
      ),
      onPhoneClick = {},
      onConfirmRemoval = {},
      onCancelRemoval = {},
      onToggleBackground = {},
    )
  }
}
