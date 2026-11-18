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
import com.serhii.tvmarketplace.core.testing.FakeLinkShareSettings
import com.serhii.tvmarketplace.core.testing.FakePairedPhones
import com.serhii.tvmarketplace.core.ui.AppTheme
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class SettingsTest {

  @get:Rule
  val compose = createComposeRule()

  private val phones = FakePairedPhones()
  private val settings = FakeLinkShareSettings()

  @Test
  fun `lists phones, asks before removing, and toggles background receiving`() = runTest {
    val phone = phones.add("Safari on iPhone", PairedPhoneKind.BROWSER, "hash")
    val viewModel = SettingsViewModel(phones, settings, backgroundScope)
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      var state = awaitItem()
      while (state.phones.isEmpty()) state = awaitItem()
      assertEquals(listOf("Safari on iPhone"), state.phones.map { it.name })
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
