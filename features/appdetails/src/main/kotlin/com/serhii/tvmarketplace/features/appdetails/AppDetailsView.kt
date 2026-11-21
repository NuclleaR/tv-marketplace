package com.serhii.tvmarketplace.features.appdetails

import android.text.format.Formatter
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.serhii.tvmarketplace.core.domain.AppStatus
import com.serhii.tvmarketplace.core.domain.FailureReason
import com.serhii.tvmarketplace.core.ui.AppIcon
import com.serhii.tvmarketplace.core.ui.AppTheme
import com.serhii.tvmarketplace.core.ui.ListRow
import com.serhii.tvmarketplace.core.ui.PillButton
import com.serhii.tvmarketplace.core.ui.PreviewTv
import com.serhii.tvmarketplace.core.ui.ProgressLine
import com.serhii.tvmarketplace.core.ui.ScreenTitle
import com.serhii.tvmarketplace.core.ui.StatusLine
import com.serhii.tvmarketplace.core.ui.TvScreen
import com.serhii.tvmarketplace.core.ui.failureText
import com.serhii.tvmarketplace.core.ui.icons.AppIcons
import com.serhii.tvmarketplace.core.ui.relativeTime
import com.serhii.tvmarketplace.features.appdetails.AppDetailsViewState.Content
import com.serhii.tvmarketplace.features.appdetails.AppDetailsViewState.PackageTag
import com.serhii.tvmarketplace.features.appdetails.AppDetailsViewState.PrimaryAction
import java.time.Instant
import com.serhii.tvmarketplace.core.strings.R as StringsR
import com.serhii.tvmarketplace.features.appdetails.AppDetailsViewState.Dialog as DetailsDialog

// the buttons of the screen; the one pressed last gets the focus back when the screen returns
internal enum class DetailsButton { PRIMARY, NOTES, SETTINGS, REMOVE }

@Composable
internal fun AppDetailsView(
  viewState: AppDetailsViewState,
  onPrimary: () -> Unit,
  onPackage: (Long) -> Unit,
  onReleaseNotes: () -> Unit,
  onSettings: () -> Unit,
  onRemove: () -> Unit,
  onOpenPermissionSettings: () -> Unit,
  onPermissionLater: () -> Unit,
  onUseAutomaticChoice: () -> Unit,
  onTogglePrereleases: () -> Unit,
  onConfirmRemove: (uninstall: Boolean) -> Unit,
  onDismissDialog: () -> Unit,
  modifier: Modifier = Modifier,
) {
  TvScreen(modifier) {
    when (viewState) {
      AppDetailsViewState.Loading -> Unit
      AppDetailsViewState.Missing -> Text(stringResource(StringsR.string.details_missing), style = MaterialTheme.typography.bodyLarge)
      is Content -> Details(
        viewState,
        onPrimary,
        onPackage,
        onReleaseNotes,
        onSettings,
        onRemove,
        onOpenPermissionSettings,
        onPermissionLater,
        onUseAutomaticChoice,
      )
    }
  }
  if (viewState is Content) {
    when (viewState.dialog) {
      DetailsDialog.SETTINGS -> SettingsDialog(viewState.name, viewState.includePrereleases, onTogglePrereleases, onDismissDialog)
      DetailsDialog.REMOVE -> RemoveDialog(viewState.name, viewState.installed, onConfirmRemove, onDismissDialog)
      null -> Unit
    }
  }
}

@Composable
private fun Details(
  content: Content,
  onPrimary: () -> Unit,
  onPackage: (Long) -> Unit,
  onReleaseNotes: () -> Unit,
  onSettings: () -> Unit,
  onRemove: () -> Unit,
  onOpenPermissionSettings: () -> Unit,
  onPermissionLater: () -> Unit,
  onUseAutomaticChoice: () -> Unit,
) {
  var lastPressed by rememberSaveable { mutableStateOf(DetailsButton.PRIMARY) }
  val requesters = remember { DetailsButton.entries.associateWith { FocusRequester() } }
  // asked once the buttons are laid out: a request before that is dropped
  var focusPlaced by remember { mutableStateOf(false) }
  val press: (DetailsButton, () -> Unit) -> Unit = { button, action ->
    lastPressed = button
    action()
  }
  Column {
    Header(content)
    if (content.needsPermission) {
      PermissionPanel(onOpenPermissionSettings, onPermissionLater, Modifier.padding(top = 24.dp))
    } else {
      Row(
        modifier = Modifier
          .padding(top = 24.dp, start = 6.dp)
          .focusRestorer(requesters.getValue(lastPressed))
          .focusGroup()
          .onGloballyPositioned {
            if (!focusPlaced) {
              focusPlaced = true
              requesters.getValue(lastPressed).requestFocus()
            }
          },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        PillButton(
          text = primaryText(content.primary),
          onClick = { press(DetailsButton.PRIMARY, onPrimary) },
          modifier = Modifier.focusRequester(requesters.getValue(DetailsButton.PRIMARY)),
        )
        PillButton(
          text = stringResource(StringsR.string.details_release_notes),
          onClick = { press(DetailsButton.NOTES, onReleaseNotes) },
          modifier = Modifier.focusRequester(requesters.getValue(DetailsButton.NOTES)),
        )
        PillButton(
          text = stringResource(StringsR.string.details_settings),
          onClick = { press(DetailsButton.SETTINGS, onSettings) },
          leadingIcon = AppIcons.Settings,
          modifier = Modifier.focusRequester(requesters.getValue(DetailsButton.SETTINGS)),
        )
        PillButton(
          text = stringResource(StringsR.string.details_remove),
          onClick = { press(DetailsButton.REMOVE, onRemove) },
          leadingIcon = AppIcons.Trash,
          modifier = Modifier.focusRequester(requesters.getValue(DetailsButton.REMOVE)),
        )
        (content.status as? AppStatus.Downloading)?.let { ProgressLine(it.fraction, Modifier.width(160.dp)) }
      }
    }
    // a closed dialog gives the focus back to the button that opened it
    LaunchedEffect(content.dialog) {
      if (content.dialog == null && focusPlaced) requesters.getValue(lastPressed).requestFocus()
    }
    content.failure?.let { Notice(failureText(it), Modifier.padding(top = 18.dp)) }
    when {
      content.status == AppStatus.Unavailable -> Notice(stringResource(StringsR.string.details_unavailable), Modifier.padding(top = 18.dp))
      content.status is AppStatus.NoPackage && content.latest != null ->
        Notice(stringResource(StringsR.string.details_no_package), Modifier.padding(top = 18.dp))
    }
    Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
      Column(Modifier.weight(1.2F)) {
        if (content.latest != null && content.packages.isNotEmpty()) {
          Packages(content, onPackage, onUseAutomaticChoice)
        }
      }
      Column(Modifier.weight(1F)) {
        content.notesHtml?.let { html ->
          Text(
            stringResource(StringsR.string.details_whats_new),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            remember(html) { plainNotes(html) },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
          )
        }
      }
    }
  }
}

// the notes' formatting without links, which nothing on a TV can follow
internal fun plainNotes(html: String): AnnotatedString {
  val parsed = AnnotatedString.fromHtml(html)
  return AnnotatedString(parsed.text.trim(), parsed.spanStyles, parsed.paragraphStyles)
}

@Composable
private fun Header(content: Content) {
  Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
    AppIcon(name = content.name, iconUrl = content.iconUrl, size = 88.dp)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      ScreenTitle(content.name)
      Text(content.ownerAndRepo, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        StatusLine(content.status)
        Text(
          when {
            content.latest != null -> stringResource(
              StringsR.string.details_latest,
              content.latest.version,
              relativeTime(content.latest.publishedAt),
            )
            content.checking -> stringResource(StringsR.string.home_checking)
            else -> stringResource(StringsR.string.details_no_release)
          },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      content.description?.let {
        Text(
          it,
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.width(640.dp),
        )
      }
    }
  }
}

@Composable
private fun primaryText(action: PrimaryAction): String = stringResource(
  when (action) {
    PrimaryAction.INSTALL -> StringsR.string.details_install
    PrimaryAction.UPDATE -> StringsR.string.details_update
    PrimaryAction.OPEN -> StringsR.string.details_open
    PrimaryAction.CANCEL -> StringsR.string.details_cancel
    PrimaryAction.INSTALL_NOW -> StringsR.string.details_install_now
    PrimaryAction.RETRY -> StringsR.string.details_retry
    PrimaryAction.RELEASE_NOTES -> StringsR.string.details_release_notes
    PrimaryAction.REMOVE -> StringsR.string.details_remove
    PrimaryAction.CHECK -> StringsR.string.home_check
  },
)

@Composable
private fun Packages(
  content: Content,
  onPackage: (Long) -> Unit,
  onUseAutomaticChoice: () -> Unit,
) {
  val context = LocalContext.current
  val chosenFocus = remember { FocusRequester() }
  val chosenId = content.packages.firstOrNull { it.tag == PackageTag.YOUR_CHOICE }?.id
    ?: content.packages.firstOrNull { it.tag == PackageTag.RECOMMENDED }?.id
  Column {
    Text(
      stringResource(StringsR.string.details_packages, content.latest?.version.orEmpty()),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (content.choiceLost) {
      Text(
        stringResource(StringsR.string.details_choice_lost),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
      )
    }
    // entering the list lands on the package that would be installed, then on the one focused last
    LazyColumn(
      modifier = Modifier.focusRestorer(chosenFocus),
      contentPadding = PaddingValues(top = 10.dp, bottom = 27.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      items(content.packages, key = { it.id }, contentType = { "package" }) { row ->
        ListRow(
          onClick = { onPackage(row.id) },
          modifier = (if (row.id == chosenId) Modifier.focusRequester(chosenFocus) else Modifier).fillMaxWidth(),
          headline = {
            Text(
              row.fileName,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              color = if (row.compatible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          },
          supporting = { Text(Formatter.formatShortFileSize(context, row.sizeBytes)) },
          trailing = row.tag?.let { tag -> { Text(tagText(tag), style = MaterialTheme.typography.labelLarge) } },
        )
      }
      if (content.userChoice) {
        item(key = "automatic", contentType = "automatic") {
          ListRow(
            onClick = onUseAutomaticChoice,
            modifier = Modifier.fillMaxWidth(),
            headline = { Text(stringResource(StringsR.string.details_use_automatic)) },
          )
        }
      }
    }
  }
}

@Composable
private fun tagText(tag: PackageTag): String = stringResource(
  when (tag) {
    PackageTag.RECOMMENDED -> StringsR.string.details_recommended
    PackageTag.YOUR_CHOICE -> StringsR.string.details_your_choice
    PackageTag.NOT_FOR_DEVICE -> StringsR.string.details_not_for_device
  },
)

@Composable
private fun Notice(
  text: String,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .width(720.dp)
      .border(1.dp, MaterialTheme.colorScheme.border, RoundedCornerShape(14.dp))
      .padding(16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(AppIcons.Alert, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    Text(text, style = MaterialTheme.typography.bodyLarge)
  }
}

@Composable
private fun PermissionPanel(
  onOpenSettings: () -> Unit,
  onLater: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val openFocus = remember { FocusRequester() }
  var placed by remember { mutableStateOf(false) }
  Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Notice(stringResource(StringsR.string.details_permission))
    Row(
      modifier = Modifier.padding(start = 6.dp).focusGroup().onGloballyPositioned {
        if (!placed) {
          placed = true
          openFocus.requestFocus()
        }
      },
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      PillButton(
        stringResource(StringsR.string.details_permission_open),
        onClick = onOpenSettings,
        modifier = Modifier.focusRequester(openFocus),
      )
      PillButton(stringResource(StringsR.string.details_permission_later), onClick = onLater)
    }
  }
}

@Composable
private fun SettingsDialog(
  name: String,
  includePrereleases: Boolean,
  onToggle: () -> Unit,
  onDismiss: () -> Unit,
) {
  val toggleFocus = remember { FocusRequester() }
  val state = stringResource(if (includePrereleases) StringsR.string.settings_on else StringsR.string.settings_off)
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(shape = RoundedCornerShape(20.dp), colors = SurfaceDefaults.colors(containerColor = AppTheme.surfaces.mid)) {
      Column(modifier = Modifier.width(520.dp).padding(28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(stringResource(StringsR.string.app_settings_title, name), style = MaterialTheme.typography.headlineSmall)
        // the whole row is the control: the switch only shows the state
        ListRow(
          onClick = onToggle,
          modifier = Modifier.fillMaxWidth().focusRequester(toggleFocus).semantics { stateDescription = state },
          headline = { Text(stringResource(StringsR.string.app_settings_prereleases), fontWeight = FontWeight.Medium) },
          supporting = { Text(stringResource(StringsR.string.app_settings_prereleases_hint)) },
          trailing = { Switch(checked = includePrereleases, onCheckedChange = null) },
        )
      }
    }
  }
  LaunchedEffect(Unit) { toggleFocus.requestFocus() }
}

@Composable
private fun RemoveDialog(
  name: String,
  installed: Boolean,
  onConfirm: (uninstall: Boolean) -> Unit,
  onCancel: () -> Unit,
) {
  val safeFocus = remember { FocusRequester() }
  // the platform's default dialog width is too narrow for three buttons side by side
  Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(shape = RoundedCornerShape(20.dp), colors = SurfaceDefaults.colors(containerColor = AppTheme.surfaces.mid)) {
      Column(modifier = Modifier.widthIn(min = 560.dp).padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(StringsR.string.remove_title, name), style = MaterialTheme.typography.headlineSmall)
        Row(modifier = Modifier.padding(top = 14.dp, start = 6.dp, end = 6.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
          // the safe choice is the default one: the app stays on the TV
          if (installed) {
            PillButton(stringResource(StringsR.string.remove_keep), onClick = {
              onConfirm(false)
            }, modifier = Modifier.focusRequester(safeFocus))
            PillButton(stringResource(StringsR.string.remove_uninstall), onClick = { onConfirm(true) }, leadingIcon = AppIcons.Trash)
          } else {
            PillButton(stringResource(StringsR.string.remove_confirm), onClick = {
              onConfirm(false)
            }, modifier = Modifier.focusRequester(safeFocus))
          }
          PillButton(stringResource(StringsR.string.remove_cancel), onClick = onCancel)
        }
      }
    }
  }
  LaunchedEffect(Unit) { safeFocus.requestFocus() }
}

private val previewContent = Content(
  name = "SmartTube",
  ownerAndRepo = "yuliskov/SmartTube",
  iconUrl = null,
  description = "Ad-free YouTube for Android TV",
  status = AppStatus.UpdateAvailable("32.79", "32.80s"),
  primary = PrimaryAction.UPDATE,
  failure = null,
  latest = AppDetailsViewState.Latest("32.80s", Instant.now().minusSeconds(3 * 86_400)),
  checking = false,
  packages = listOf(
    AppDetailsViewState.PackageRow(1, "SmartTube_stable_32.80_armeabi-v7a.apk", 18_000_000, PackageTag.RECOMMENDED, compatible = true),
    AppDetailsViewState.PackageRow(2, "SmartTube_stable_32.80_universal.apk", 41_000_000, null, compatible = true),
    AppDetailsViewState.PackageRow(3, "SmartTube_stable_32.80_x86.apk", 20_000_000, PackageTag.NOT_FOR_DEVICE, compatible = false),
  ),
  choiceLost = false,
  userChoice = false,
  notesHtml = "<h3>What's new</h3><ul><li>Faster search</li><li>Fixed playback on Android 14</li></ul>",
  needsPermission = false,
  installed = true,
  includePrereleases = false,
  dialog = null,
)

@Composable
private fun Preview(viewState: AppDetailsViewState) {
  AppTheme {
    AppDetailsView(viewState, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
  }
}

@PreviewTv
@Composable
private fun AppDetailsUpdatePreview() = Preview(previewContent)

@PreviewTv
@Composable
private fun AppDetailsDownloadingPreview() =
  Preview(previewContent.copy(status = AppStatus.Downloading(0.4F), primary = PrimaryAction.CANCEL))

@PreviewTv
@Composable
private fun AppDetailsFailedPreview() =
  Preview(previewContent.copy(status = AppStatus.NotInstalled, primary = PrimaryAction.RETRY, failure = FailureReason.SignatureConflict))

@PreviewTv
@Composable
private fun AppDetailsPermissionPreview() = Preview(previewContent.copy(status = AppStatus.ReadyToInstall, needsPermission = true))

@PreviewTv
@Composable
private fun AppDetailsNoPackagePreview() = Preview(
  previewContent.copy(
    status = AppStatus.NoPackage(null),
    primary = PrimaryAction.RELEASE_NOTES,
    packages = previewContent.packages.takeLast(1),
  ),
)
