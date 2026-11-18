package com.serhii.tvmarketplace.features.addapp.typeit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.serhii.tvmarketplace.core.domain.GitHubLinkParser
import com.serhii.tvmarketplace.core.ui.AppTheme
import com.serhii.tvmarketplace.core.ui.PillButton
import com.serhii.tvmarketplace.core.ui.PreviewTv
import com.serhii.tvmarketplace.core.ui.ScreenTitle
import com.serhii.tvmarketplace.core.ui.TvScreen
import com.serhii.tvmarketplace.core.ui.icons.AppIcons
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
    ScreenTitle(stringResource(StringsR.string.type_it_title))
    Column(modifier = Modifier.fillMaxSize().padding(start = 32.dp), verticalArrangement = Arrangement.Center) {
      Text(
        stringResource(StringsR.string.type_it_label),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      RepositoryField(
        value = viewState.input,
        onValueChange = onInputChange,
        invalid = viewState.invalid,
        onGo = onContinue,
        modifier = Modifier.padding(top = 10.dp).focusRequester(fieldFocus),
      )
      Text(
        stringResource(if (viewState.invalid) StringsR.string.type_it_invalid else StringsR.string.type_it_example),
        style = MaterialTheme.typography.bodyMedium,
        color = if (viewState.invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 18.dp),
      )
      PillButton(
        text = stringResource(StringsR.string.type_it_continue),
        onClick = onContinue,
        trailingIcon = AppIcons.ArrowForward,
        modifier = Modifier.padding(top = 32.dp),
      )
    }
  }
  LaunchedEffect(Unit) { fieldFocus.requestFocus() }
}

// a TV text field: quiet at rest, ringed and slightly larger when focused like every other control
@Composable
private fun RepositoryField(
  value: String,
  onValueChange: (String) -> Unit,
  invalid: Boolean,
  onGo: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var focused by remember { mutableStateOf(false) }
  val colors = MaterialTheme.colorScheme
  val shape = RoundedCornerShape(12.dp)
  val ring = when {
    invalid -> colors.error
    focused -> colors.onSurface
    else -> Color.Transparent
  }
  BasicTextField(
    value = value,
    onValueChange = onValueChange,
    singleLine = true,
    textStyle = MaterialTheme.typography.titleLarge.copy(color = colors.onSurface),
    cursorBrush = SolidColor(colors.primary),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
    keyboardActions = KeyboardActions(onGo = { onGo() }),
    modifier = modifier
      .width(550.dp)
      .onFocusChanged { focused = it.isFocused }
      .graphicsLayer {
        val scale = if (focused) 1.05F else 1F
        scaleX = scale
        scaleY = scale
      }
      .border(2.dp, ring, shape)
      .padding(2.dp)
      .testTag(TYPE_IT_FIELD),
    decorationBox = { field ->
      Box(
        Modifier.height(52.dp).background(AppTheme.surfaces.mid, shape).padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
      ) {
        if (value.isEmpty()) {
          Text(stringResource(StringsR.string.type_it_hint), style = MaterialTheme.typography.titleLarge, color = colors.onSurfaceVariant)
        }
        field()
      }
    },
  )
}

@Composable
fun TypeItScreen(viewModel: TypeItViewModel) {
  TypeItView(viewState = viewModel.state, onInputChange = viewModel::onInputChange, onContinue = viewModel::onContinue)
}

@PreviewTv
@Composable
private fun TypeItViewPreview() {
  AppTheme { TypeItView(viewState = TypeItViewState(), onInputChange = {}, onContinue = {}) }
}

@PreviewTv
@Composable
private fun TypeItViewInvalidPreview() {
  AppTheme { TypeItView(viewState = TypeItViewState(input = "ImranR98/", invalid = true), onInputChange = {}, onContinue = {}) }
}
