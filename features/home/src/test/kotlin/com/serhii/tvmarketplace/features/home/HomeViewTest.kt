package com.serhii.tvmarketplace.features.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.serhii.tvmarketplace.core.domain.AppStatus
import com.serhii.tvmarketplace.core.domain.FailureReason
import com.serhii.tvmarketplace.core.installer.UpdateBatch
import com.serhii.tvmarketplace.core.ui.AppTheme
import com.serhii.tvmarketplace.navigation.Destination
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

// a tile is a card: the focused node is the card that holds the text
private fun SemanticsNodeInteraction.assertIsFocusedOrHasFocusedParent() {
  val _ = assert(isFocused() or androidx.compose.ui.test.hasParent(isFocused()) or androidx.compose.ui.test.hasAnyAncestor(isFocused()))
}

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

  private fun press(key: Key) {
    val _ = compose.onRoot().performKeyInput { pressKey(key) }
  }

  @Test
  fun `an app tile opens the app's details and gets the focus back`() {
    var home by mutableStateOf(true)
    val opened = mutableListOf<Destination>()
    val apps = listOf(
      HomeViewState.App(1, "Obtainium", "ImranR98/Obtainium", status = AppStatus.UpdateAvailable("1.7.3", "1.7.4")),
      HomeViewState.App(2, "SmartTube", "yuliskov/SmartTube", status = AppStatus.UpToDate("32.80")),
    )
    compose.setContent {
      AppTheme {
        val holder = rememberSaveableStateHolder()
        if (home) {
          holder.SaveableStateProvider("home") {
            HomeView(
              viewState = HomeViewState(apps = apps),
              onOpen = {
                opened += it
                home = false
              },
            )
          }
        }
      }
    }
    compose.onNodeWithText("1.7.3 → 1.7.4").assertExists()
    compose.onNodeWithText("Installed 32.80").assertExists()
    press(Key.DirectionDown)
    press(Key.DirectionRight)
    compose.onNodeWithText("SmartTube").assertIsFocusedOrHasFocusedParent()
    press(Key.DirectionCenter)
    assertEquals(listOf<Destination>(Destination.AppDetails(2)), opened)
    compose.waitForIdle()
    home = true
    compose.waitForIdle()
    compose.onNodeWithText("SmartTube").assertIsFocusedOrHasFocusedParent()
  }

  @Test
  fun `back from an app that was removed there, focus starts at add app`() {
    var home by mutableStateOf(true)
    var apps by mutableStateOf(
      listOf(HomeViewState.App(1, "Obtainium", "ImranR98/Obtainium"), HomeViewState.App(2, "SmartTube", "yuliskov/SmartTube")),
    )
    compose.setContent {
      AppTheme {
        val holder = rememberSaveableStateHolder()
        if (home) {
          holder.SaveableStateProvider("home") { HomeView(viewState = HomeViewState(apps = apps), onOpen = { home = false }) }
        }
      }
    }
    press(Key.DirectionDown)
    press(Key.DirectionRight)
    press(Key.DirectionCenter)
    compose.waitForIdle()
    apps = apps.take(1)
    home = true
    compose.waitForIdle()
    compose.onNodeWithText("Add app").assertIsFocused()
  }

  @Test
  fun `updates show their count, and update all is one press down from the top`() {
    var updated = 0
    compose.setContent {
      AppTheme {
        HomeView(
          viewState = HomeViewState(
            apps = listOf(HomeViewState.App(1, "Obtainium", "ImranR98/Obtainium")),
            updates = HomeViewState.Updates(count = 2),
          ),
          onOpen = {},
          onUpdateAll = { updated++ },
        )
      }
    }
    compose.onNodeWithText("2 updates").assertExists()
    press(Key.DirectionDown)
    compose.onNodeWithText("Update all").assertIsFocused()
    press(Key.DirectionCenter)
    assertEquals(1, updated)
  }

  @Test
  fun `while updating it can stop after the current app, and failures can be shown after`() {
    var state by mutableStateOf(
      HomeViewState.Updates(count = 3, batch = UpdateBatch(total = 3, done = 1, failed = emptyMap(), stopping = false, running = true)),
    )
    var stopped = 0
    var shown = 0
    compose.setContent {
      AppTheme {
        HomeView(
          viewState = HomeViewState(apps = emptyList(), updates = state),
          onOpen = {},
          onStopAfterCurrent = { stopped++ },
          onShowFailures = { shown++ },
        )
      }
    }
    compose.onNodeWithText("Updating 2 of 3").assertExists()
    compose.onNodeWithText("Stop after this one").performClickByKey()
    assertEquals(1, stopped)
    state =
      HomeViewState.Updates(
        count = 1,
        batch = UpdateBatch(total = 3, done = 3, failed = mapOf(2L to FailureReason.Network), stopping = false, running = false),
      )
    compose.onNodeWithText("2 updated, 1 failed").assertExists()
    compose.onNodeWithText("Show").performClickByKey()
    assertEquals(1, shown)
  }

  @Test
  fun `the request limit says when to try again`() {
    compose.setContent {
      AppTheme {
        HomeView(
          viewState = HomeViewState(
            apps = emptyList(),
            updates = HomeViewState.Updates(resumeAt = Instant.parse("2026-11-20T14:20:00Z")),
          ),
          onOpen = {
          },
        )
      }
    }
    compose.onNodeWithText("GitHub is limiting requests", substring = true).assertExists()
  }

  // focus the node by moving there from Add app, then press Select
  private fun androidx.compose.ui.test.SemanticsNodeInteraction.performClickByKey() {
    val _ = performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
    press(Key.DirectionCenter)
  }
}
