package com.serhii.tvmarketplace.features.home

import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.turbine.test
import com.serhii.tvmarketplace.core.domain.GitHubRepoRef
import com.serhii.tvmarketplace.core.testing.FakeTrackedApps
import com.serhii.tvmarketplace.core.testing.previewOf
import com.serhii.tvmarketplace.navigation.Destination
import com.serhii.tvmarketplace.navigation.Navigator
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeViewModelTest {

  private val trackedApps = FakeTrackedApps(GitHubRepoRef("ImranR98", "Obtainium"))
  private val navigator = mockk<Navigator>(relaxUnitFun = true)
  private val viewModel =
    HomeViewModel(
      trackedApps,
      com.serhii.tvmarketplace.core.testing.FakeIncomingLinks(),
      com.serhii.tvmarketplace.core.linkserver.IncomingLinkEvents(),
      navigator,
    )

  @Test
  fun `lists the tracked apps`() = runTest {
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      var state = awaitItem()
      while (state.apps.isEmpty()) state = awaitItem()
      assertEquals(listOf(HomeViewState.App(id = 1, name = "Obtainium", ownerAndRepo = "ImranR98/Obtainium")), state.apps)
    }
  }

  @Test
  fun `a tracked app shows its icon`() = runTest {
    val ref = GitHubRepoRef("f-droid", "fdroidclient")
    val _ = trackedApps.add(previewOf(ref).copy(iconUrl = "https://raw/icon.png", avatarUrl = "https://avatars/1"))
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      var state = awaitItem()
      while (state.apps.size < 2) state = awaitItem()
      val app = state.apps.single { it.ownerAndRepo == "f-droid/fdroidclient" }
      assertEquals("https://raw/icon.png", app.iconUrl)
    }
  }

  @Test
  fun `add app opens the add screen`() {
    viewModel.onOpen(Destination.AddApp)
    verify { navigator.goTo(Destination.AddApp) }
  }
}
