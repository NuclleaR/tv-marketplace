package com.serhii.tvmarketplace.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation3.runtime.NavEntry
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.serhii.tvmarketplace.core.common.rootGraphAs
import com.serhii.tvmarketplace.core.data.LinkShareSettings
import com.serhii.tvmarketplace.core.data.PairedPhoneKind
import com.serhii.tvmarketplace.core.data.PairedPhonesRepository
import com.serhii.tvmarketplace.core.linkserver.LinkServer
import com.serhii.tvmarketplace.core.linkserver.ServerState
import com.serhii.tvmarketplace.core.ui.AppTheme
import com.serhii.tvmarketplace.core.ui.FocusCard
import com.serhii.tvmarketplace.core.ui.PillButton
import com.serhii.tvmarketplace.core.ui.PreviewTv
import com.serhii.tvmarketplace.core.ui.ScreenTitle
import com.serhii.tvmarketplace.core.ui.TvScreen
import com.serhii.tvmarketplace.core.ui.icons.AppIcons
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
  // "ip:port" while the link server runs, for the tip about reserving the address
  val tvAddress: String? = null,
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
  private val server: LinkServer,
  private val scope: CoroutineScope,
) {

  private var confirmRemovalId by mutableStateOf<String?>(null)

  @Composable
  fun viewState(): SettingsViewState {
    val paired by remember { phones.phones() }.collectAsState(initial = emptyList())
    val background by remember { settings.receiveInBackground }.collectAsState(initial = true)
    val serverState by server.state.collectAsState()
    val items = paired.map { SettingsViewState.Phone(id = it.id, name = it.displayName, kind = it.kind, lastUsedAt = it.lastUsedAt) }
    return SettingsViewState(
      phones = items,
      receiveInBackground = background,
      tvAddress = (serverState as? ServerState.Running)?.let { "${it.ip}:${it.port}" },
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
  val phoneFocus = remember { FocusRequester() }
  val toggleFocus = remember { FocusRequester() }
  TvScreen(modifier) {
    ScreenTitle(stringResource(StringsR.string.settings_title))
    Row(modifier = Modifier.padding(top = 32.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
      Column(Modifier.width(500.dp).padding(horizontal = 28.dp)) {
        Text(
          stringResource(StringsR.string.settings_phones),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (viewState.phones.isEmpty()) {
          Text(
            stringResource(StringsR.string.settings_no_phones),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 14.dp),
          )
        } else {
          LazyColumn(contentPadding = PaddingValues(top = 14.dp, bottom = 27.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(viewState.phones, key = { it.id }) { phone ->
              val focus = if (phone == viewState.phones.first()) Modifier.focusRequester(phoneFocus) else Modifier
              PhoneRow(phone, onClick = { onPhoneClick(phone.id) }, modifier = focus)
            }
          }
        }
      }
      Column(Modifier.weight(1F).padding(top = 34.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BackgroundToggle(viewState.receiveInBackground, onToggleBackground, Modifier.focusRequester(toggleFocus))
        Tip(viewState.tvAddress)
      }
    }
  }
  LaunchedEffect(viewState.phones.isEmpty()) {
    if (viewState.phones.isEmpty()) toggleFocus.requestFocus() else phoneFocus.requestFocus()
  }
  viewState.confirmRemoval?.let { phone -> RemoveDialog(phone.name, onConfirmRemoval, onCancelRemoval) }
}

@Composable
private fun PhoneRow(
  phone: SettingsViewState.Phone,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var focused by remember { mutableStateOf(false) }
  FocusCard(
    onClick = onClick,
    focusedScale = 1.05F,
    cornerRadius = 14.dp,
    modifier = modifier.fillMaxWidth().height(64.dp).onFocusChanged { focused = it.hasFocus || it.isFocused },
  ) {
    Row(
      modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 18.dp),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(Modifier.size(40.dp).clip(CircleShape).background(AppTheme.surfaces.high), contentAlignment = Alignment.Center) {
        Icon(AppIcons.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
      }
      Column(Modifier.weight(1F), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          phone.name,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          stringResource(
            if (phone.kind ==
              PairedPhoneKind.BROWSER
            ) {
              StringsR.string.settings_kind_browser
            } else {
              StringsR.string.settings_kind_companion
            },
          ),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          stringResource(StringsR.string.settings_last_used, relativeTime(phone.lastUsedAt)),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (focused) {
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Trash, contentDescription = null, modifier = Modifier.size(13.dp))
            Text(stringResource(StringsR.string.settings_select_to_forget), style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    }
  }
}

@Composable
private fun BackgroundToggle(
  on: Boolean,
  onToggle: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  val state = stringResource(if (on) StringsR.string.settings_on else StringsR.string.settings_off)
  FocusCard(
    onClick = onToggle,
    focusedScale = 1.03F,
    cornerRadius = 14.dp,
    modifier = modifier.fillMaxWidth().semantics { stateDescription = state },
  ) {
    Row(
      modifier = Modifier.padding(18.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(Modifier.weight(1F), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
          stringResource(StringsR.string.settings_background),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Medium,
        )
        Text(
          stringResource(StringsR.string.settings_background_summary),
          style = MaterialTheme.typography.bodySmall,
          color = colors.onSurfaceVariant,
        )
      }
      // a drawn switch: the whole card is the control, so the remote has one thing to select
      Box(
        Modifier.width(52.dp).height(30.dp).clip(CircleShape).background(if (on) colors.primary else AppTheme.surfaces.high).padding(4.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
      ) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(if (on) colors.onPrimary else colors.onSurfaceVariant))
      }
    }
  }
}

@Composable
private fun Tip(tvAddress: String?) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.borderVariant, RoundedCornerShape(14.dp))
      .padding(18.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(AppIcons.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
        stringResource(StringsR.string.settings_tip),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(stringResource(StringsR.string.settings_ip_tip), style = MaterialTheme.typography.bodyLarge)
      tvAddress?.let {
        Text(
          stringResource(StringsR.string.settings_tv_address, it),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun RemoveDialog(
  name: String,
  onConfirm: () -> Unit,
  onCancel: () -> Unit,
) {
  val cancelFocus = remember { FocusRequester() }
  Dialog(onDismissRequest = onCancel) {
    Surface(shape = RoundedCornerShape(20.dp), colors = SurfaceDefaults.colors(containerColor = AppTheme.surfaces.mid)) {
      Column(modifier = Modifier.width(480.dp).padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(StringsR.string.settings_remove_title, name), style = MaterialTheme.typography.headlineSmall)
        Text(
          stringResource(StringsR.string.settings_remove_body),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.padding(top = 14.dp, start = 6.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
          // the safe choice is the default one
          PillButton(stringResource(StringsR.string.common_cancel), onClick = onCancel, modifier = Modifier.focusRequester(cancelFocus))
          PillButton(stringResource(StringsR.string.settings_remove), onClick = onConfirm, leadingIcon = AppIcons.Trash)
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

@PreviewTv
@Composable
private fun SettingsViewPreview() {
  AppTheme {
    SettingsView(
      viewState = SettingsViewState(
        phones = listOf(
          SettingsViewState.Phone("1", "Safari on iPhone", PairedPhoneKind.BROWSER, Instant.now()),
          SettingsViewState.Phone("2", "Pixel 8", PairedPhoneKind.COMPANION, Instant.now().minusSeconds(7200)),
        ),
        receiveInBackground = true,
        confirmRemoval = null,
        tvAddress = "192.168.1.42:8765",
      ),
      onPhoneClick = {},
      onConfirmRemoval = {},
      onCancelRemoval = {},
      onToggleBackground = {},
    )
  }
}
