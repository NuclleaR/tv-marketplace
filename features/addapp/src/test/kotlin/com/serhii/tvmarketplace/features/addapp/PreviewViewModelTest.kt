package com.serhii.tvmarketplace.features.addapp

import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.turbine.test
import com.serhii.tvmarketplace.core.data.IncomingLinkStatus
import com.serhii.tvmarketplace.core.data.PreviewResult
import com.serhii.tvmarketplace.core.domain.GitHubRepoRef
import com.serhii.tvmarketplace.core.testing.FakeGitHub
import com.serhii.tvmarketplace.core.testing.FakeIncomingLinks
import com.serhii.tvmarketplace.core.testing.FakeTrackedApps
import com.serhii.tvmarketplace.features.addapp.preview.PreviewViewModel
import com.serhii.tvmarketplace.features.addapp.preview.PreviewViewState
import com.serhii.tvmarketplace.navigation.Destination
import com.serhii.tvmarketplace.navigation.Navigator
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PreviewViewModelTest {

  private val ref = GitHubRepoRef("ImranR98", "Obtainium")
  private val gitHub = FakeGitHub()
  private val trackedApps = FakeTrackedApps()
  private val incomingLinks = FakeIncomingLinks()
  private val navigator = mockk<Navigator>(relaxUnitFun = true)

  private fun viewModel(destination: Destination.Preview) = PreviewViewModel(gitHub, trackedApps, incomingLinks, navigator, destination)

  @Test
  fun `loads the preview of a received link`() = runTest {
    val link = incomingLinks.receive("text", ref, senderPhoneId = null, senderLabel = "Safari on iPhone")
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel(Destination.Preview(linkId = link.id)).viewState() }.test {
      val content = assertIs<PreviewViewState.Content>(awaitMatching { it != PreviewViewState.Loading })
      assertEquals("Obtainium", content.name)
      assertEquals("ImranR98/Obtainium", content.ownerAndRepo)
      assertEquals("v1.0.0", content.latestVersion)
      assertEquals("Safari on iPhone", content.sender)
      assertEquals(false, content.alreadyTracked)
    }
  }

  @Test
  fun `adds the app and marks the link as added`() = runTest {
    val link = incomingLinks.receive("text", ref, null, "Phone")
    val viewModel = viewModel(Destination.Preview(linkId = link.id))
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      val _ = assertIs<PreviewViewState.Content>(awaitMatching { it != PreviewViewState.Loading })
      viewModel.onAdd()
      runCurrent()
      assertEquals(PreviewViewState.Added(name = "Obtainium"), expectMostRecentItem())
    }
    assertEquals(listOf(ref), trackedApps.apps.value.map { it.ref })
    assertEquals(IncomingLinkStatus.ADDED, incomingLinks.get(link.id)?.status)
  }

  @Test
  fun `cancel leaves the link pending and goes back`() = runTest {
    val link = incomingLinks.receive("text", ref, null, "Phone")
    viewModel(Destination.Preview(linkId = link.id)).onCancel()
    verify { navigator.goBack() }
    assertEquals(IncomingLinkStatus.PENDING, incomingLinks.get(link.id)?.status)
  }

  @Test
  fun `an app that is already tracked offers open instead of add`() = runTest {
    val _ = trackedApps.add(com.serhii.tvmarketplace.core.testing.previewOf(ref))
    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel(Destination.Preview(owner = ref.owner, repo = ref.repo)).viewState()
    }.test {
      assertTrue(assertIs<PreviewViewState.Content>(awaitMatching { it != PreviewViewState.Loading }).alreadyTracked)
    }
  }

  @Test
  fun `errors can be retried`() = runTest {
    gitHub.result = { PreviewResult.NetworkError }
    val viewModel = viewModel(Destination.Preview(owner = ref.owner, repo = ref.repo))
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      assertEquals(PreviewViewState.Error(PreviewViewState.ErrorKind.Network), awaitMatching { it != PreviewViewState.Loading })
      gitHub.result = { PreviewResult.Found(com.serhii.tvmarketplace.core.testing.previewOf(it)) }
      viewModel.onRetry()
      runCurrent()
      val _ = assertIs<PreviewViewState.Content>(expectMostRecentItem())
    }
  }

  @Test
  fun `maps not found and rate limits, and a link that is gone`() = runTest {
    gitHub.result = { PreviewResult.NotFound }
    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel(Destination.Preview(owner = "a", repo = "b")).viewState()
    }.test {
      assertEquals(PreviewViewState.Error(PreviewViewState.ErrorKind.NotFound), awaitMatching { it != PreviewViewState.Loading })
    }
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel(Destination.Preview(linkId = 99)).viewState() }.test {
      assertEquals(PreviewViewState.Error(PreviewViewState.ErrorKind.LinkMissing), awaitMatching { it != PreviewViewState.Loading })
    }
  }
}
