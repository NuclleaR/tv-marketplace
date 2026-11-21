package com.serhii.tvmarketplace.features.home

import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.turbine.test
import com.serhii.tvmarketplace.core.domain.AppStatus
import com.serhii.tvmarketplace.core.domain.GitHubRepoRef
import com.serhii.tvmarketplace.core.installer.DownloadState
import com.serhii.tvmarketplace.core.testing.FakeDownloads
import com.serhii.tvmarketplace.core.testing.FakeInstallQueue
import com.serhii.tvmarketplace.core.testing.FakeInstalledApps
import com.serhii.tvmarketplace.core.testing.FakeReleasesRepository
import com.serhii.tvmarketplace.core.testing.FakeTrackedApps
import com.serhii.tvmarketplace.core.testing.installedApp
import com.serhii.tvmarketplace.core.testing.pkg
import com.serhii.tvmarketplace.core.testing.previewOf
import com.serhii.tvmarketplace.core.testing.release
import com.serhii.tvmarketplace.navigation.Destination
import com.serhii.tvmarketplace.navigation.Navigator
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeViewModelTest {

  private val trackedApps = FakeTrackedApps(GitHubRepoRef("ImranR98", "Obtainium"))
  private val navigator = mockk<Navigator>(relaxUnitFun = true)
  private val releases = FakeReleasesRepository()
  private val installedApps = FakeInstalledApps()
  private val downloads = FakeDownloads()
  private val queue = FakeInstallQueue()
  private val viewModel =
    homeViewModel(
      trackedApps,
      navigator = navigator,
      releases = releases,
      installedApps = installedApps,
      downloads = downloads,
      queue = queue,
    )

  @Test
  fun `lists the tracked apps`() = runTest {
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      var state = awaitItem()
      while (state.apps.isEmpty()) state = awaitItem()
      assertEquals(
        listOf(HomeViewState.App(id = 1, name = "Obtainium", ownerAndRepo = "ImranR98/Obtainium", status = AppStatus.NoRelease)),
        state.apps,
      )
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

  @Test
  fun `apps show whether they are installed or have an update, and the updates are counted`() = runTest {
    trackedApps.update(1) { it.copy(packageName = "dev.imranr.obtainium") }
    releases.store(1, release("v1.7.4"), null, Instant.EPOCH)
    installedApps.install(installedApp("dev.imranr.obtainium", versionName = "1.7.3"))
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      var state = awaitItem()
      while (state.updates.count == 0) state = awaitItem()
      assertEquals(AppStatus.UpdateAvailable("1.7.3", "v1.7.4"), state.apps.single().status)
      assertEquals(1, state.updates.count)
      assertEquals(true, state.updates.visible)
      viewModel.onUpdateAll()
      assertEquals(listOf(1L), queue.updateAlls.single().map { it.appId })
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `a download in progress shows on its tile`() = runTest {
    releases.store(1, release("v1.7.4"), null, Instant.EPOCH)
    downloads.start(1, pkg("app-universal-release.apk"), null)
    downloads.move(1, DownloadState.Running(25, 100))
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      var state = awaitItem()
      while (state.apps.isEmpty()) state = awaitItem()
      assertEquals(AppStatus.Downloading(0.25F), state.apps.single().status)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `selecting an app opens its details`() {
    viewModel.onOpen(Destination.AppDetails(1))
    verify { navigator.goTo(Destination.AppDetails(1)) }
  }
}
