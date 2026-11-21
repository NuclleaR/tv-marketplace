package com.serhii.tvmarketplace.features.home

import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.turbine.test
import com.serhii.tvmarketplace.core.domain.GitHubRepoRef
import com.serhii.tvmarketplace.core.linkserver.IncomingLinkEvents
import com.serhii.tvmarketplace.core.testing.FakeIncomingLinks
import com.serhii.tvmarketplace.core.testing.FakeTrackedApps
import com.serhii.tvmarketplace.navigation.Destination
import com.serhii.tvmarketplace.navigation.Navigator
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IncomingNoticeTest {

  private val incomingLinks = FakeIncomingLinks()
  private val events = IncomingLinkEvents()
  private val navigator = mockk<Navigator>(relaxUnitFun = true)
  private val viewModel = homeViewModel(FakeTrackedApps(), incomingLinks, events, navigator)

  @Test
  fun `a link received on home shows a notice for 8 seconds and counts in the inbox`() = runTest {
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      runCurrent()
      val link = incomingLinks.receive("a", GitHubRepoRef("a", "b"), null, "Safari on iPhone")
      events.emit(link.id, link.ref, link.senderLabel)
      runCurrent()
      val state = expectMostRecentItem()
      assertEquals(HomeViewState.Notice(linkId = link.id, sender = "Safari on iPhone"), state.notice)
      assertEquals(1, state.inboxCount)
      advanceTimeBy(NOTICE_DURATION_MS + 1)
      assertNull(expectMostRecentItem().notice)
    }
  }

  @Test
  fun `the notice opens the preview, the buttons open inbox and settings`() {
    viewModel.onOpenNotice(7)
    verify { navigator.goTo(Destination.Preview(linkId = 7)) }
    viewModel.onOpen(Destination.Inbox)
    verify { navigator.goTo(Destination.Inbox) }
    viewModel.onOpen(Destination.Settings)
    verify { navigator.goTo(Destination.Settings) }
  }
}
