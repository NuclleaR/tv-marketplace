package com.serhii.tvmarketplace.features.inbox

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.turbine.test
import com.serhii.tvmarketplace.core.data.IncomingLinkStatus
import com.serhii.tvmarketplace.core.domain.GitHubRepoRef
import com.serhii.tvmarketplace.core.testing.FakeIncomingLinks
import com.serhii.tvmarketplace.core.testing.FakeTrackedApps
import com.serhii.tvmarketplace.core.ui.AppTheme
import com.serhii.tvmarketplace.navigation.Destination
import com.serhii.tvmarketplace.navigation.Navigator
import io.mockk.mockk
import io.mockk.verify
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
class InboxTest {

  @get:Rule
  val compose = createComposeRule()

  private val incomingLinks = FakeIncomingLinks()
  private val trackedApps = FakeTrackedApps(GitHubRepoRef("already", "tracked"))
  private val navigator = mockk<Navigator>(relaxUnitFun = true)

  @Test
  fun `lists pending links newest first and flags tracked ones`() = runTest {
    val _ = incomingLinks.receive("a", GitHubRepoRef("ImranR98", "Obtainium"), null, "Safari on iPhone")
    val _ = incomingLinks.receive("b", GitHubRepoRef("already", "tracked"), null, "Chrome on Android")
    val viewModel = InboxViewModel(incomingLinks, trackedApps, navigator, backgroundScope)
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      var state = awaitItem()
      while (state.items.size < 2) state = awaitItem()
      assertEquals(listOf("tracked", "Obtainium"), state.items.map { it.name })
      assertEquals(listOf(true, false), state.items.map { it.alreadyTracked })
    }
  }

  @Test
  fun `opening goes to the preview, dismissing removes the link`() = runTest {
    val link = incomingLinks.receive("a", GitHubRepoRef("a", "b"), null, "Phone")
    val viewModel = InboxViewModel(incomingLinks, trackedApps, navigator, backgroundScope)
    viewModel.onOpen(link.id)
    verify { navigator.goTo(Destination.Preview(linkId = link.id)) }
    viewModel.onDismiss(link.id)
    runCurrent()
    assertEquals(IncomingLinkStatus.DISMISSED, incomingLinks.get(link.id)?.status)
  }

  private val item = InboxViewState.Item(
    id = 1,
    name = "Obtainium",
    ownerAndRepo = "ImranR98/Obtainium",
    sender = "Safari on iPhone",
    receivedAt = Instant.now(),
    alreadyTracked = false,
  )

  @Test
  fun `the first link is focused and select opens it`() {
    var opened = 0L
    compose.setContent {
      AppTheme {
        InboxView(
          viewState = InboxViewState(items = listOf(item, item.copy(id = 2, name = "Other"))),
          onOpen = { opened = it },
          onDismiss = {},
          onAddApp = {},
        )
      }
    }
    compose.onNodeWithText("Obtainium").assertIsFocused()
    compose.onAllNodesWithText("From Safari on iPhone, just now", substring = true).onFirst().assertExists()
    compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
    compose.onNodeWithText("Other").assertIsFocused()
    compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
    assertEquals(2L, opened)
  }

  @Test
  fun `menu dismisses the focused link`() {
    var dismissed = 0L
    compose.setContent {
      AppTheme { InboxView(viewState = InboxViewState(items = listOf(item)), onOpen = {}, onDismiss = { dismissed = it }, onAddApp = {}) }
    }
    compose.onRoot().performKeyInput { pressKey(Key.Menu) }
    assertEquals(1L, dismissed)
  }

  @Test
  fun `empty inbox focuses add app`() {
    compose.setContent {
      AppTheme { InboxView(viewState = InboxViewState(items = emptyList()), onOpen = {}, onDismiss = {}, onAddApp = {}) }
    }
    compose.onNodeWithText("No links yet. Send one from your phone.").assertExists()
    compose.onNodeWithText("Add app").assertIsFocused()
  }
}
