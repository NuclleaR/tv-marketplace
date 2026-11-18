package com.serhii.tvmarketplace.features.addapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.serhii.tvmarketplace.core.ui.AppTheme
import com.serhii.tvmarketplace.features.addapp.preview.PreviewView
import com.serhii.tvmarketplace.features.addapp.preview.PreviewViewState
import org.junit.Rule
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class AddAppViewTest {

  @get:Rule
  val compose = createComposeRule()

  private val ready = AddAppViewState.Ready(
    qrContent = "http://192.168.1.42:8765/s/token",
    address = "http://192.168.1.42:8765",
    pin = "482 913",
  )

  @Test
  fun `add app focuses type it instead and shows the address and code`() {
    compose.setContent { AppTheme { AddAppView(viewState = ready, onTypeIt = {}, onRetry = {}) } }
    compose.onNodeWithText("Type it instead").assertIsFocused()
    compose.onNodeWithText("Or open http://192.168.1.42:8765 on your phone and enter the code 482 913").assertExists()
    compose.onNodeWithContentDescription("QR code to send a link from your phone").assertExists()
  }

  @Test
  fun `select on type it instead opens typing`() {
    var typed = 0
    compose.setContent { AppTheme { AddAppView(viewState = ready, onTypeIt = { typed++ }, onRetry = {}) } }
    compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
    assertEquals(1, typed)
  }

  @Test
  fun `without network retry is focused`() {
    compose.setContent { AppTheme { AddAppView(viewState = AddAppViewState.NoNetwork, onTypeIt = {}, onRetry = {}) } }
    compose.onNodeWithText("Retry").assertIsFocused()
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
