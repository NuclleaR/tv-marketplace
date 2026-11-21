package com.serhii.tvmarketplace.features.appdetails.notes

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.serhii.tvmarketplace.core.ui.AppTheme
import com.serhii.tvmarketplace.core.ui.PreviewTv
import com.serhii.tvmarketplace.core.ui.ScreenTitle
import com.serhii.tvmarketplace.core.ui.TvScreen
import com.serhii.tvmarketplace.features.appdetails.plainNotes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.serhii.tvmarketplace.core.strings.R as StringsR

internal const val NOTES_TAG = "releaseNotes"

// the notes are one focusable block: Up and Down scroll it a few lines, a held key repeats
@Composable
internal fun ReleaseNotesView(
  viewState: ReleaseNotesViewState?,
  modifier: Modifier = Modifier,
  scroll: ScrollState = rememberScrollState(),
) {
  val focus = remember { FocusRequester() }
  val scope = rememberCoroutineScope()
  val step = with(LocalDensity.current) { 72.dp.toPx() }
  TvScreen(modifier) {
    if (viewState == null) return@TvScreen
    ScreenTitle(listOfNotNull(viewState.name, viewState.version).joinToString(" "))
    val notes = remember(viewState.notesHtml) { viewState.notesHtml?.let(::plainNotes) }
    Column(
      Modifier
        .padding(top = 24.dp)
        .widthIn(max = 840.dp)
        .fillMaxWidth()
        .testTag(NOTES_TAG)
        .focusRequester(focus)
        .onKeyEvent { event -> event.type == KeyEventType.KeyDown && scroll(scope, scroll, event.key, step) }
        .focusable()
        .verticalScroll(scroll),
    ) {
      Text(
        notes ?: androidx.compose.ui.text.AnnotatedString(stringResource(StringsR.string.details_no_release)),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(bottom = 27.dp),
      )
    }
  }
  LaunchedEffect(viewState != null) { if (viewState != null) focus.requestFocus() }
}

private fun scroll(
  scope: CoroutineScope,
  state: ScrollState,
  key: Key,
  step: Float,
): Boolean {
  val by = when (key) {
    Key.DirectionDown -> step
    Key.DirectionUp -> -step
    else -> return false
  }
  scope.launch { val _ = state.animateScrollBy(by) }
  return true
}

@PreviewTv
@Composable
private fun ReleaseNotesPreview() {
  AppTheme {
    ReleaseNotesView(
      ReleaseNotesViewState(
        "SmartTube",
        "32.80s",
        "<h3>What's new</h3><ul><li>Faster <a href=\"https://x\">search</a></li><li>Fixed playback on Android 14</li></ul><p>Thanks to everyone who reported bugs.</p>",
      ),
    )
  }
}
