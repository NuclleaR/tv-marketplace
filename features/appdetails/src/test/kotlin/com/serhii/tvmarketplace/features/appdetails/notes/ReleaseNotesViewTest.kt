package com.serhii.tvmarketplace.features.appdetails.notes

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.serhii.tvmarketplace.core.ui.AppTheme
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
@RunWith(AndroidJUnit4::class)
class ReleaseNotesViewTest {

  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `the notes take the focus and scroll with up and down, without links`() {
    val scroll = ScrollState(0)
    val long = (1..80).joinToString("") { "<p>Change number $it in this release</p>" } + "<p>See <a href=\"https://x\">the docs</a></p>"
    compose.setContent { AppTheme { ReleaseNotesView(ReleaseNotesViewState("SmartTube", "v1.3", long), scroll = scroll) } }
    compose.onNodeWithTag(NOTES_TAG).assertIsFocused()
    compose.onNodeWithText("SmartTube v1.3").assertExists()
    repeat(3) { compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) } }
    compose.waitForIdle()
    assertTrue(scroll.value > 0)
    repeat(10) { compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) } }
    compose.waitForIdle()
    assertEquals(0, scroll.value)
    compose.onNodeWithText("See the docs", substring = true).assertExists()
  }
}
