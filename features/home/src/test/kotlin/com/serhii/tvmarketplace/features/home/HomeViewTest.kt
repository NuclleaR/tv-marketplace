package com.serhii.tvmarketplace.features.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.serhii.tvmarketplace.core.ui.AppTheme
import com.serhii.tvmarketplace.navigation.Destination
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
          onOpen = { if (it == Destination.AddApp) added++ },
        )
      }
    }
    compose.onNodeWithText("Add app").assertIsFocused()
    compose.onNodeWithText("Obtainium").assertExists()
    compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
    assertEquals(1, added)
  }

  @Test
  fun `the actions and the apps each remember their last focused item`() {
    val apps = List(3) { HomeViewState.App(it.toLong(), "App $it", "owner/app$it") }
    compose.setContent { AppTheme { HomeView(viewState = HomeViewState(apps = apps, inboxCount = 1), onOpen = {}) } }
    fun press(key: Key) {
      val _ = compose.onRoot().performKeyInput { pressKey(key) }
    }
    // into the apps from Add app, then over to the last one
    press(Key.DirectionDown)
    press(Key.DirectionRight)
    press(Key.DirectionRight)
    compose.onNodeWithText("App 2").assertIsFocused()
    // up returns to the action that was focused last, not to the one above
    press(Key.DirectionUp)
    compose.onNodeWithText("Add app").assertIsFocused()
    press(Key.DirectionRight)
    compose.onNodeWithText("Inbox").assertIsFocused()
    // down returns to the app that was focused last
    press(Key.DirectionDown)
    compose.onNodeWithText("App 2").assertIsFocused()
    press(Key.DirectionUp)
    compose.onNodeWithText("Inbox").assertIsFocused()
  }

  @Test
  fun `back from another screen focus returns to the action that opened it`() {
    var home by mutableStateOf(true)
    compose.setContent {
      AppTheme {
        // what Navigation3 does with a screen under the top of the back stack
        val holder = rememberSaveableStateHolder()
        if (home) {
          holder.SaveableStateProvider("home") {
            HomeView(viewState = HomeViewState(apps = emptyList(), inboxCount = 1), onOpen = { if (it == Destination.Inbox) home = false })
          }
        }
      }
    }
    compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
    compose.onNodeWithText("Inbox").assertIsFocused()
    compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
    compose.waitForIdle()
    home = true
    compose.waitForIdle()
    compose.onNodeWithText("Inbox").assertIsFocused()
  }

  @Test
  fun `without apps it says how to add one`() {
    compose.setContent { AppTheme { HomeView(viewState = HomeViewState(apps = emptyList()), onOpen = {}) } }
    compose.onNodeWithText("No apps yet. Add one from your phone.").assertExists()
  }
}
