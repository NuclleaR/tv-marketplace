package com.serhii.tvmarketplace.features.home.failures

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.serhii.tvmarketplace.core.domain.FailureReason
import com.serhii.tvmarketplace.core.ui.AppTheme
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
@RunWith(AndroidJUnit4::class)
class UpdateFailuresViewTest {

  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `the first failure is focused, each says why, and select opens the app`() {
    val opened = mutableListOf<Long>()
    compose.setContent {
      AppTheme {
        UpdateFailuresView(
          listOf(
            UpdateFailure(4, "SmartTube", null, FailureReason.SignatureConflict),
            UpdateFailure(7, "Kodi", null, FailureReason.Network),
          ),
          onOpen = { opened += it },
        )
      }
    }
    val _ = compose.onNodeWithText("SmartTube").assert(isFocused() or hasAnyAncestor(isFocused()))
    compose.onNodeWithText("No connection", substring = true).assertExists()
    compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
    compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
    assertEquals(listOf(7L), opened)
  }
}
