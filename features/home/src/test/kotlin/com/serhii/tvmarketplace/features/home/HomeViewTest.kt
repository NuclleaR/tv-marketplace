package com.serhii.tvmarketplace.features.home

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
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

// laid out on a 1080p TV screen, not the default phone window
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
@RunWith(AndroidJUnit4::class)
class HomeViewTest {

  @get:Rule
  val compose = createComposeRule()

  @Test
  fun `add app is focused first and lists tracked apps below`() {
    var added = 0
    compose.setContent {
      AppTheme {
        HomeView(
          viewState = HomeViewState(apps = listOf(HomeViewState.App(1, "Obtainium", "ImranR98/Obtainium"))),
          onAddApp = { added++ },
        )
      }
    }
    compose.onNodeWithText("Add app").assertIsFocused()
    compose.onNodeWithText("Obtainium").assertExists()
    compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
    assertEquals(1, added)
  }

  @Test
  fun `without apps it says how to add one`() {
    compose.setContent { AppTheme { HomeView(viewState = HomeViewState(apps = emptyList()), onAddApp = {}) } }
    compose.onNodeWithText("No apps yet. Add one from your phone.").assertExists()
  }
}
