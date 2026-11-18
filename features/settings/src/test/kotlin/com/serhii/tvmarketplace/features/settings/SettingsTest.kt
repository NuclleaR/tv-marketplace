package com.serhii.tvmarketplace.features.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.turbine.test
import com.serhii.tvmarketplace.core.data.PairedPhoneKind
import com.serhii.tvmarketplace.core.linkserver.LinkServer
import com.serhii.tvmarketplace.core.linkserver.ServerState
import com.serhii.tvmarketplace.core.testing.FakeLinkShareSettings
import com.serhii.tvmarketplace.core.testing.FakePairedPhones
import com.serhii.tvmarketplace.core.ui.AppTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

// laid out on a 1080p TV screen, not the default phone window
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
@RunWith(AndroidJUnit4::class)
class SettingsTest {

  @get:Rule
  val compose = createComposeRule()

  private val phones = FakePairedPhones()
  private val settings = FakeLinkShareSettings()
  private val server = mockk<LinkServer> {
    every { state } returns MutableStateFlow<ServerState>(ServerState.Running(ip = "192.168.1.42", port = 8765))
  }

  @Test
  fun `lists phones, asks before removing, and toggles background receiving`() = runTest {
    val phone = phones.add("Safari on iPhone", PairedPhoneKind.BROWSER, "hash")
    val viewModel = SettingsViewModel(phones, settings, server, backgroundScope)
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      var state = awaitItem()
      while (state.phones.isEmpty()) state = awaitItem()
      assertEquals(listOf("Safari on iPhone"), state.phones.map { it.name })
      assertEquals("192.168.1.42:8765", state.tvAddress)
      viewModel.onPhoneClick(phone.id)
      runCurrent()
      assertEquals(phone.id, expectMostRecentItem().confirmRemoval?.id)
      viewModel.onConfirmRemoval()
      runCurrent()
      assertEquals(emptyList(), expectMostRecentItem().phones)
      viewModel.onToggleBackground()
      runCurrent()
      assertEquals(false, expectMostRecentItem().receiveInBackground)
    }
  }

  @Test
  fun `the remove dialog focuses cancel`() {
    var removed = 0
    val phone = SettingsViewState.Phone("1", "Safari on iPhone", PairedPhoneKind.BROWSER, Instant.now())
    compose.setContent {
      AppTheme {
        SettingsView(
          viewState = SettingsViewState(phones = listOf(phone), receiveInBackground = true, confirmRemoval = phone),
          onPhoneClick = {},
          onConfirmRemoval = { removed++ },
          onCancelRemoval = {},
          onToggleBackground = {},
        )
      }
    }
    compose.onNodeWithText("Cancel").assertIsFocused()
    compose.onNodeWithText("Cancel").performKeyInput { pressKey(Key.DirectionCenter) }
    assertEquals(0, removed)
  }

  @Test
  fun `the first phone is focused and the tip shows the TV's address`() {
    val phone = SettingsViewState.Phone("1", "Safari on iPhone", PairedPhoneKind.BROWSER, Instant.now())
    compose.setContent {
      AppTheme {
        SettingsView(
          viewState = SettingsViewState(
            phones = listOf(phone),
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
    compose.onNodeWithText("Safari on iPhone", substring = true).assertIsFocused()
    compose.onNodeWithText("This TV: 192.168.1.42:8765").assertExists()
  }

  @Test
  fun `the background toggle is focused first`() {
    compose.setContent {
      AppTheme {
        SettingsView(
          viewState = SettingsViewState(phones = emptyList(), receiveInBackground = true, confirmRemoval = null),
          onPhoneClick = {},
          onConfirmRemoval = {},
          onCancelRemoval = {},
          onToggleBackground = {},
        )
      }
    }
    compose.onNodeWithText("Receive links in the background").assertIsFocused()
    compose.onNodeWithText("No phones are remembered yet.").assertExists()
  }
}
