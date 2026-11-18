package com.serhii.tvmarketplace.features.addapp.typeit

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.serhii.tvmarketplace.core.domain.GitHubLinkParser
import com.serhii.tvmarketplace.core.ui.AppTheme
import com.serhii.tvmarketplace.core.ui.TvScreen
import com.serhii.tvmarketplace.navigation.Destination
import com.serhii.tvmarketplace.navigation.Navigator
import dev.zacsweers.metro.Inject
import com.serhii.tvmarketplace.core.strings.R as StringsR

data class TypeItViewState(
  val input: String = "",
  val invalid: Boolean = false,
)

@Inject
class TypeItViewModel(private val navigator: Navigator) {

  var state by mutableStateOf(TypeItViewState())
    private set

  fun onInputChange(input: String) {
    state = TypeItViewState(input = input, invalid = false)
  }

  fun onContinue() {
    val ref = GitHubLinkParser.parseUserInput(state.input)
    if (ref == null) {
      state = state.copy(invalid = true)
    } else {
      navigator.goTo(Destination.Preview(owner = ref.owner, repo = ref.repo))
    }
  }
}

internal const val TYPE_IT_FIELD = "type_it_field"

@Composable
internal fun TypeItView(
  viewState: TypeItViewState,
  onInputChange: (String) -> Unit,
  onContinue: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val fieldFocus = remember { FocusRequester() }
  TvScreen(modifier) {
    Text(stringResource(StringsR.string.add_app_title), style = MaterialTheme.typography.displaySmall)
    OutlinedTextField(
      value = viewState.input,
      onValueChange = onInputChange,
      singleLine = true,
      isError = viewState.invalid,
      placeholder = { androidx.compose.material3.Text(stringResource(StringsR.string.type_it_hint)) },
      supportingText = if (viewState.invalid) {
        { androidx.compose.material3.Text(stringResource(StringsR.string.type_it_invalid)) }
      } else {
        null
      },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
      keyboardActions = KeyboardActions(onGo = { onContinue() }),
      modifier = Modifier.padding(top = 32.dp).fillMaxWidth(0.6f).focusRequester(fieldFocus).testTag(TYPE_IT_FIELD),
    )
    Button(onClick = onContinue, modifier = Modifier.padding(top = 16.dp)) {
      Text(stringResource(StringsR.string.type_it_continue))
    }
  }
  LaunchedEffect(Unit) { fieldFocus.requestFocus() }
}

@Composable
fun TypeItScreen(viewModel: TypeItViewModel) {
  TypeItView(viewState = viewModel.state, onInputChange = viewModel::onInputChange, onContinue = viewModel::onContinue)
}

@Preview(device = "id:tv_1080p")
@Composable
private fun TypeItViewPreview() {
  AppTheme { TypeItView(viewState = TypeItViewState(input = "ImranR98/", invalid = true), onInputChange = {}, onContinue = {}) }
}
