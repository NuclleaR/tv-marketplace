package com.serhii.tvmarketplace.features.addapp

import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.serhii.tvmarketplace.core.ui.AppIcon
import com.serhii.tvmarketplace.core.ui.AppTheme
import com.serhii.tvmarketplace.core.ui.KeyCap
import com.serhii.tvmarketplace.core.ui.ListRow
import com.serhii.tvmarketplace.core.ui.PillButton
import com.serhii.tvmarketplace.core.ui.PreviewTv
import com.serhii.tvmarketplace.core.ui.ScreenTitle
import com.serhii.tvmarketplace.core.ui.TvScreen
import com.serhii.tvmarketplace.core.ui.icons.AppIcons
import io.github.nucllear.tvkeyboard.KeyType
import io.github.nucllear.tvkeyboard.KeyboardLayout
import io.github.nucllear.tvkeyboard.TvKeyboard
import com.serhii.tvmarketplace.core.strings.R as StringsR

// everything a GitHub owner or repository name can hold, plus the slash between them; GitHub ignores case
private val gitHubLayout = KeyboardLayout("abcdefghijklmnopqrstuvwxyz0123456789-_./", switchLabel = "abc")
private const val KEYS_PER_ROW = 8
private val keySize = 40.dp
private val keyGap = 8.dp
private val keyboardOuterPadding = 6.dp
private val keyboardWidth: Dp = keySize * KEYS_PER_ROW + keyGap * (KEYS_PER_ROW - 1) + keyboardOuterPadding * 2
private val qrSize = 184.dp

@Composable
internal fun AddAppView(
  viewState: AddAppViewState,
  onCharacter: (String) -> Unit,
  onBackspace: () -> Unit,
  onOwnerClick: (String) -> Unit,
  onRepoClick: (SearchResults.Repo) -> Unit,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
  onNetworkSettings: () -> Unit = {},
  onFDroidClick: (SearchResults.FDroidApp) -> Unit = {},
) {
  val keyboardFocus = remember { FocusRequester() }
  val openedFocus = remember { FocusRequester() }
  // the repository a preview was opened from: coming back focuses it instead of the keyboard
  var opened by rememberSaveable { mutableStateOf<String?>(null) }
  val results = viewState.results
  val openedShown = when (results) {
    is SearchResults.Repos -> results.repos.any { it.key == opened }
    is SearchResults.Matches -> results.repos.any { it.key == opened } || results.fdroid.any { it.key == opened }
    else -> false
  }
  TvScreen(modifier) {
    ScreenTitle(stringResource(StringsR.string.add_app_title))
    Row(
      modifier = Modifier.fillMaxSize().padding(top = 20.dp),
      horizontalArrangement = Arrangement.spacedBy(40.dp),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        QueryField(viewState.query, viewState.searching)
        val erase = stringResource(StringsR.string.add_app_backspace)
        TvKeyboard(
          onCharacter = onCharacter,
          onBackspace = onBackspace,
          layouts = listOf(gitHubLayout),
          keysPerRow = KEYS_PER_ROW,
          space = false,
          focusFirstKey = opened == null,
          keySize = keySize,
          gap = keyGap,
          outerPadding = keyboardOuterPadding,
          modifier = Modifier.focusRequester(keyboardFocus),
        ) { key, onClick, keyModifier ->
          KeyCap(
            label = key.label,
            onClick = onClick,
            modifier = keyModifier,
            contentDescription = if (key.type == KeyType.Backspace) erase else null,
          )
        }
      }
      Box(Modifier.weight(1f).fillMaxSize()) {
        when (results) {
          // with text typed, None is the moment before the first answer, e.g. back from a preview
          SearchResults.None -> if (viewState.query.isEmpty()) PhonePanel(viewState.phone, onRetry, onNetworkSettings)
          is SearchResults.Matches -> LazyColumn(
            modifier = Modifier.fillMaxSize().focusRestorer(),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            if (results.fdroid.isNotEmpty()) {
              item(key = "fdroid", contentType = "heading") { SectionHeading(stringResource(StringsR.string.add_app_section_fdroid)) }
            }
            items(results.fdroid, key = { it.key }, contentType = { "fdroid" }) { app ->
              FDroidRow(
                app = app,
                onClick = {
                  opened = app.key
                  onFDroidClick(app)
                },
                modifier = if (app.key == opened) Modifier.focusRequester(openedFocus) else Modifier,
              )
            }
            if (results.repos.isNotEmpty()) {
              item(key = "repos", contentType = "heading") {
                SectionHeading(
                  stringResource(StringsR.string.add_app_section_repos),
                  if (results.fdroid.isNotEmpty()) Modifier.padding(top = 12.dp) else Modifier,
                )
              }
            }
            items(results.repos, key = { "repo:${it.key}" }, contentType = { "repo" }) { repo ->
              RepoRow(
                repo = repo,
                onClick = {
                  opened = repo.key
                  onRepoClick(repo)
                },
                modifier = if (repo.key == opened) Modifier.focusRequester(openedFocus) else Modifier,
                showOwner = true,
              )
            }
            if (results.owners.isNotEmpty()) {
              item(key = "owners", contentType = "heading") {
                SectionHeading(stringResource(StringsR.string.add_app_section_owners), Modifier.padding(top = 12.dp))
              }
            }
            items(results.owners, key = { "owner:${it.login}" }, contentType = { "owner" }) { owner ->
              OwnerRow(
                owner = owner,
                onClick = {
                  onOwnerClick(owner.login)
                  // the row is replaced by the owner's repositories: carry on typing a name, or go right to pick one
                  keyboardFocus.requestFocus()
                },
              )
            }
          }
          is SearchResults.Repos -> Column {
            Text(stringResource(StringsR.string.add_app_owner_repos, results.owner), style = MaterialTheme.typography.titleMedium)
            if (results.partial) {
              Text(
                stringResource(StringsR.string.add_app_more_repos),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
              )
            }
            LazyColumn(
              modifier = Modifier.fillMaxSize().focusRestorer(),
              contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              items(results.repos, key = { it.key }) { repo ->
                RepoRow(
                  repo = repo,
                  onClick = {
                    opened = repo.key
                    onRepoClick(repo)
                  },
                  modifier = if (repo.key == opened) Modifier.focusRequester(openedFocus) else Modifier,
                )
              }
            }
          }
          SearchResults.KeepTyping -> Message(stringResource(StringsR.string.add_app_keep_typing))
          SearchResults.NothingFound -> Message(stringResource(StringsR.string.add_app_nothing_found))
          is SearchResults.OwnerNotFound -> Message(stringResource(StringsR.string.add_app_owner_not_found, results.owner))
          SearchResults.RateLimited -> Message(stringResource(StringsR.string.preview_rate_limited))
          SearchResults.NetworkError -> Message(stringResource(StringsR.string.preview_network_error))
        }
      }
    }
  }
  // back from a preview the results load again: focus waits for the repository it was opened from, or, when it is
  // gone, goes to the keyboard
  var restoring by remember { mutableStateOf(opened != null) }
  LaunchedEffect(openedShown, results, viewState.searching) {
    if (!restoring) return@LaunchedEffect
    when {
      openedShown -> openedFocus.requestFocus()
      results != SearchResults.None && !viewState.searching -> keyboardFocus.requestFocus()
      else -> return@LaunchedEffect
    }
    restoring = false
  }
}

// shows what the keyboard typed; not focusable, the keyboard owns every key press
@Composable
private fun QueryField(
  query: String,
  searching: Boolean,
) {
  Row(
    modifier = Modifier
      .width(keyboardWidth)
      .height(56.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(AppTheme.surfaces.low)
      .padding(horizontal = 18.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(AppIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    Box(Modifier.weight(1f).padding(start = 12.dp)) {
      if (query.isEmpty()) {
        Text(
          stringResource(StringsR.string.add_app_search_hint),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      } else {
        // the start is cut off rather than the end: what was typed last is what the viewer checks
        Text(
          query.takeLast(28),
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
        )
      }
    }
    if (searching) {
      Text(
        stringResource(StringsR.string.add_app_searching),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun OwnerRow(
  owner: SearchResults.Owner,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  ListRow(
    onClick = onClick,
    modifier = modifier.fillMaxWidth(),
    leading = { AppIcon(name = owner.login, iconUrl = owner.avatarUrl, size = 40.dp) },
    headline = { Text(owner.login, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    supporting = if (owner.organization) {
      { Text(stringResource(StringsR.string.add_app_organization)) }
    } else {
      null
    },
  )
}

// found by name among everyone's repositories, the owner tells apart forks and namesakes
@Composable
private fun RepoRow(
  repo: SearchResults.Repo,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  showOwner: Boolean = false,
) {
  val supporting = listOfNotNull(repo.owner.takeIf { showOwner }, repo.description).joinToString(" · ").ifEmpty { null }
  ListRow(
    onClick = onClick,
    modifier = modifier.fillMaxWidth(),
    headline = { Text(repo.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    supporting = supporting?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
  )
}

@Composable
private fun FDroidRow(
  app: SearchResults.FDroidApp,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  ListRow(
    onClick = onClick,
    modifier = modifier.fillMaxWidth(),
    leading = { AppIcon(name = app.name, iconUrl = app.iconUrl, size = 40.dp, packageName = app.packageName) },
    headline = { Text(app.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    supporting = app.summary?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
  )
}

@Composable
private fun SectionHeading(
  text: String,
  modifier: Modifier = Modifier,
) {
  Text(
    text,
    style = MaterialTheme.typography.titleMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.semantics { heading() },
  )
}

@Composable
private fun Message(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.titleMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(top = 8.dp),
  )
}

// while nothing is typed: send the link from the phone instead
@Composable
private fun PhonePanel(
  phone: PhoneState,
  onRetry: () -> Unit,
  onNetworkSettings: () -> Unit,
) {
  when (phone) {
    is PhoneState.Ready -> Column {
      Text(stringResource(StringsR.string.add_app_phone_title), style = MaterialTheme.typography.titleLarge)
      Row(modifier = Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        // a light panel around the code: phone cameras read dark modules on white best
        Box(Modifier.size(qrSize).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(14.dp)) {
          QrCode(
            content = phone.qrContent,
            contentDescription = stringResource(StringsR.string.add_app_qr_description),
            size = qrSize - 28.dp,
          )
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
          Step(1, stringResource(StringsR.string.add_app_step_scan))
          Step(2, stringResource(StringsR.string.add_app_step_send))
        }
      }
      Text(
        stringResource(StringsR.string.add_app_manual, phone.address),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp),
      )
      Text(
        phone.pin,
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        letterSpacing = 3.sp,
        modifier = Modifier.padding(top = 4.dp),
      )
      Row(
        modifier = Modifier.padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(AppIcons.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(
          stringResource(StringsR.string.add_app_same_wifi),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    PhoneState.NoNetwork -> Column {
      Icon(AppIcons.WifiOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(56.dp))
      Text(
        stringResource(StringsR.string.add_app_no_network),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 16.dp),
      )
      Text(
        stringResource(StringsR.string.add_app_no_network_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
      )
      Row(modifier = Modifier.padding(top = 24.dp, start = 10.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        PillButton(text = stringResource(StringsR.string.common_retry), onClick = onRetry, leadingIcon = AppIcons.Refresh)
        PillButton(text = stringResource(StringsR.string.add_app_network_settings), onClick = onNetworkSettings)
      }
    }
    PhoneState.Failed -> Column {
      Text(stringResource(StringsR.string.add_app_failed), style = MaterialTheme.typography.titleLarge)
      PillButton(
        text = stringResource(StringsR.string.common_retry),
        onClick = onRetry,
        leadingIcon = AppIcons.Refresh,
        modifier = Modifier.padding(top = 24.dp, start = 10.dp),
      )
    }
    PhoneState.Loading -> Unit
  }
}

@Composable
private fun Step(
  number: Int,
  text: String,
) {
  Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(
      Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        number.toString(),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
      )
    }
    Text(text, style = MaterialTheme.typography.bodyLarge)
  }
}

private val readyPhone = PhoneState.Ready("http://192.168.1.42:8765/s/abcdefghijklmnop", "http://192.168.1.42:8765", "482 913")

@Composable
private fun PreviewAddApp(viewState: AddAppViewState) {
  AppTheme {
    AddAppView(viewState = viewState, onCharacter = {}, onBackspace = {}, onOwnerClick = {}, onRepoClick = {}, onRetry = {})
  }
}

@PreviewTv
@Composable
private fun AddAppViewPreview() {
  PreviewAddApp(AddAppViewState(phone = readyPhone))
}

@PreviewTv
@Composable
private fun AddAppViewMatchesPreview() {
  PreviewAddApp(
    AddAppViewState(
      query = "jellyfin",
      phone = readyPhone,
      results = SearchResults.Matches(
        fdroid = listOf(SearchResults.FDroidApp("org.jellyfin.mobile", "Jellyfin", "Mobile client for Jellyfin", null)),
        repos = listOf(
          SearchResults.Repo("jellyfin", "jellyfin-androidtv", "Android TV client for Jellyfin"),
          SearchResults.Repo("jellyfin", "jellyfin-android", null),
        ),
        owners = listOf(
          SearchResults.Owner("jellyfin", null, organization = true),
        ),
      ),
    ),
  )
}

@PreviewTv
@Composable
private fun AddAppViewReposPreview() {
  PreviewAddApp(
    AddAppViewState(
      query = "nucllear/t",
      phone = readyPhone,
      results = SearchResults.Repos(
        owner = "NuclleaR",
        repos = listOf(
          SearchResults.Repo("NuclleaR", "tv-marketplace", "Add Android apps to your TV straight from GitHub"),
          SearchResults.Repo("NuclleaR", "tv-keyboard", "On-screen keyboard for Android TV"),
          SearchResults.Repo("NuclleaR", "android-template", null),
        ),
        partial = false,
      ),
      searching = true,
    ),
  )
}

@PreviewTv
@Composable
private fun AddAppViewNoNetworkPreview() {
  PreviewAddApp(AddAppViewState(phone = PhoneState.NoNetwork))
}
