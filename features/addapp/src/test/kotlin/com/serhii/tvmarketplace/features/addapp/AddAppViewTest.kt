package com.serhii.tvmarketplace.features.addapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.serhii.tvmarketplace.core.ui.AppTheme
import com.serhii.tvmarketplace.features.addapp.preview.PreviewView
import com.serhii.tvmarketplace.features.addapp.preview.PreviewViewState
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

// laid out on a 1080p TV screen, not the default phone window
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
@RunWith(AndroidJUnit4::class)
class AddAppViewTest {

  @get:Rule
  val compose = createComposeRule()

  private val ready = PhoneState.Ready(
    qrContent = "http://192.168.1.42:8765/s/token",
    address = "http://192.168.1.42:8765",
    pin = "482 913",
  )

  private val repos = SearchResults.Repos(
    owner = "NuclleaR",
    repos = listOf(
      SearchResults.Repo("NuclleaR", "tv-marketplace", "Apps from GitHub"),
      SearchResults.Repo("NuclleaR", "tv-keyboard", null),
    ),
    partial = false,
  )

  @Composable
  private fun addApp(
    viewState: AddAppViewState,
    onCharacter: (String) -> Unit = {},
    onBackspace: () -> Unit = {},
    onOwnerClick: (String) -> Unit = {},
    onRepoClick: (SearchResults.Repo) -> Unit = {},
    onNetworkSettings: () -> Unit = {},
  ) = AppTheme {
    AddAppView(
      viewState = viewState,
      onCharacter = onCharacter,
      onBackspace = onBackspace,
      onOwnerClick = onOwnerClick,
      onRepoClick = onRepoClick,
      onRetry = {},
      onNetworkSettings = onNetworkSettings,
    )
  }

  // the keys are built off the main thread and arrive a frame later, then the first one takes focus
  private fun awaitKeyboard() = compose.waitUntil(5_000) { compose.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }

  @Test
  fun `with nothing typed the keyboard is focused and the phone panel shows the code`() {
    compose.setContent { addApp(AddAppViewState(phone = ready)) }
    awaitKeyboard()
    compose.onNodeWithText("a").assertIsFocused()
    compose.onNodeWithText("Search GitHub: owner or owner/repo").assertExists()
    compose.onNodeWithText("Or open http://192.168.1.42:8765 on your phone and enter the code").assertExists()
    compose.onNodeWithText("482 913").assertExists()
    compose.onNodeWithContentDescription("QR code to send a link from your phone").assertExists()
  }

  @Test
  fun `the keyboard types and erases`() {
    val typed = mutableListOf<String>()
    var erased = 0
    compose.setContent { addApp(AddAppViewState(phone = ready), onCharacter = { typed += it }, onBackspace = { erased++ }) }
    awaitKeyboard()
    compose.onRoot().performKeyInput {
      pressKey(Key.DirectionCenter)
      pressKey(Key.DirectionRight)
      pressKey(Key.DirectionCenter)
    }
    compose.onNodeWithContentDescription("Erase").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
    compose.waitForIdle()
    assertEquals(listOf("a", "b"), typed)
    assertEquals(1, erased)
  }

  @Test
  fun `picking an owner hands focus back to the keyboard`() {
    var picked: String? = null
    val owners = SearchResults.Owners(listOf(SearchResults.Owner("NuclleaR", null, organization = false)))
    compose.setContent { addApp(AddAppViewState(query = "nucl", phone = ready, results = owners), onOwnerClick = { picked = it }) }
    awaitKeyboard()
    compose.onNodeWithText("NuclleaR").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
    compose.waitForIdle()
    assertEquals("NuclleaR", picked)
    compose.onNodeWithText("a").assertIsFocused()
  }

  @Test
  fun `back from a preview focuses the repository it was opened from once the results load again`() {
    var shown by mutableStateOf(true)
    var results by mutableStateOf<SearchResults>(repos)
    var openedRepo: SearchResults.Repo? = null
    compose.setContent {
      // what Navigation3 does with a screen under the top of the back stack
      val holder = rememberSaveableStateHolder()
      if (shown) {
        holder.SaveableStateProvider("addApp") {
          addApp(
            AddAppViewState(query = "nucllear/", phone = ready, results = results, searching = results == SearchResults.None),
            onRepoClick = {
              openedRepo = it
              shown = false
            },
          )
        }
      }
    }
    awaitKeyboard()
    compose.onNodeWithText("tv-keyboard").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
    compose.waitForIdle()
    assertEquals("tv-keyboard", openedRepo?.name)
    // a new view model starts without results and searches again
    results = SearchResults.None
    shown = true
    compose.waitForIdle()
    compose.onNodeWithText("a").assertIsNotFocused()
    results = repos
    compose.waitForIdle()
    compose.onNodeWithText("tv-keyboard").assertIsFocused()
  }

  @Test
  fun `results tell the owner apart from nothing found`() {
    var state by mutableStateOf(AddAppViewState(query = "nobody/", phone = ready, results = SearchResults.OwnerNotFound("nobody")))
    compose.setContent { addApp(state) }
    compose.onNodeWithText("There is no nobody on GitHub").assertExists()
    state = state.copy(query = "n", results = SearchResults.KeepTyping)
    compose.onNodeWithText("Type at least two letters of the owner's name").assertExists()
    state = state.copy(query = "nucllear/zzz", results = SearchResults.NothingFound)
    compose.onNodeWithText("Nothing found").assertExists()
  }

  @Test
  fun `without network the phone panel offers retry and network settings`() {
    var opened = 0
    compose.setContent { addApp(AddAppViewState(phone = PhoneState.NoNetwork), onNetworkSettings = { opened++ }) }
    awaitKeyboard()
    compose.onNodeWithText("Network settings").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
    compose.waitForIdle()
    assertEquals(1, opened)
    compose.onNodeWithText("Retry").assertExists()
  }

  private val content = PreviewViewState.Content(
    name = "Obtainium",
    ownerAndRepo = "ImranR98/Obtainium",
    description = "Get Android app updates straight from the source.",
    latestVersion = "v1.2.3",
    avatarUrl = null,
    sender = "Safari on iPhone",
    alreadyTracked = false,
  )

  @Composable
  private fun preview(
    state: PreviewViewState,
    onAdd: () -> Unit = {},
    onCancel: () -> Unit = {},
  ) = AppTheme { PreviewView(viewState = state, onAdd = onAdd, onOpen = {}, onCancel = onCancel, onRetry = {}) }

  @Test
  fun `preview focuses add, and right moves to cancel`() {
    var cancelled = 0
    compose.setContent { preview(content, onCancel = { cancelled++ }) }
    compose.onNodeWithText("Add").assertIsFocused()
    compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
    compose.onNodeWithText("Cancel").assertIsFocused()
    compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
    assertEquals(1, cancelled)
  }

  @Test
  fun `preview of a tracked app focuses open`() {
    compose.setContent { preview(content.copy(alreadyTracked = true)) }
    compose.onNodeWithText("Open").assertIsFocused()
    compose.onNodeWithText("Already added").assertExists()
  }

  @Test
  fun `preview without releases says so`() {
    compose.setContent { preview(content.copy(latestVersion = null)) }
    compose.onNodeWithText("No releases found").assertExists()
  }

  @Test
  fun `preview loading focuses cancel and errors focus retry`() {
    var state by mutableStateOf<PreviewViewState>(PreviewViewState.Loading)
    compose.setContent { preview(state) }
    compose.onNodeWithText("Cancel").assertIsFocused()
    state = PreviewViewState.Error(PreviewViewState.ErrorKind.Network)
    compose.waitForIdle()
    compose.onNodeWithText("Retry").assertIsFocused()
  }
}
