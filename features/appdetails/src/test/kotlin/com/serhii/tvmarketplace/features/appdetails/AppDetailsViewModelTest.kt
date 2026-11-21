package com.serhii.tvmarketplace.features.appdetails

import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.serhii.tvmarketplace.core.data.CheckResult
import com.serhii.tvmarketplace.core.data.UpdateChecker
import com.serhii.tvmarketplace.core.domain.AppStatus
import com.serhii.tvmarketplace.core.domain.FailureReason
import com.serhii.tvmarketplace.core.domain.GitHubRepoRef
import com.serhii.tvmarketplace.core.installer.ApkInfo
import com.serhii.tvmarketplace.core.installer.DownloadState
import com.serhii.tvmarketplace.core.installer.InstallJob
import com.serhii.tvmarketplace.core.installer.Installer
import com.serhii.tvmarketplace.core.installer.UninstallOutcome
import com.serhii.tvmarketplace.core.testing.FakeDownloads
import com.serhii.tvmarketplace.core.testing.FakeInstallPermission
import com.serhii.tvmarketplace.core.testing.FakeInstallQueue
import com.serhii.tvmarketplace.core.testing.FakeInstalledApps
import com.serhii.tvmarketplace.core.testing.FakeReleasesRepository
import com.serhii.tvmarketplace.core.testing.FakeTrackedApps
import com.serhii.tvmarketplace.core.testing.MutableTimeSource
import com.serhii.tvmarketplace.core.testing.installedApp
import com.serhii.tvmarketplace.core.testing.pkg
import com.serhii.tvmarketplace.core.testing.release
import com.serhii.tvmarketplace.features.appdetails.AppDetailsViewState.Content
import com.serhii.tvmarketplace.features.appdetails.AppDetailsViewState.PackageTag
import com.serhii.tvmarketplace.features.appdetails.AppDetailsViewState.PrimaryAction
import com.serhii.tvmarketplace.navigation.Destination
import com.serhii.tvmarketplace.navigation.Navigator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.File
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class AppDetailsViewModelTest {

  private val trackedApps = FakeTrackedApps(GitHubRepoRef("yuliskov", "SmartTube"))
  private val releases = FakeReleasesRepository()
  private val installedApps = FakeInstalledApps()
  private val downloads = FakeDownloads()
  private val queue = FakeInstallQueue()
  private val permission = FakeInstallPermission()
  private val installer = mockk<Installer>()
  private val checker = mockk<UpdateChecker> { coEvery { check(any()) } returns CheckResult.Done }
  private val navigator = mockk<Navigator>(relaxUnitFun = true)
  private val time = MutableTimeSource()
  private val arm64 = pkg("SmartTube_1.3_arm64-v8a.apk", id = 1)
  private val armv7 = pkg("SmartTube_1.3_armeabi-v7a.apk", id = 2)
  private val x86 = pkg("SmartTube_1.3_x86.apk", id = 3)

  private fun TestScope.viewModel() = AppDetailsViewModel(
    trackedApps,
    releases,
    installedApps,
    downloads,
    queue,
    permission,
    installer,
    checker,
    { listOf("arm64-v8a", "armeabi-v7a") },
    time,
    navigator,
    backgroundScope,
    appId = 1,
  )

  private suspend fun ReceiveTurbine<AppDetailsViewState>.content(until: (Content) -> Boolean = { true }): Content {
    while (true) {
      val state = awaitItem()
      if (state is Content && until(state)) return state
    }
  }

  private suspend fun stored() = releases.store(1, release("v1.3", arm64, armv7, x86), null, time.now)

  @Test
  fun `a tracked app that isn't installed offers install, with every package listed and the device's one recommended`() = runTest {
    stored()
    val viewModel = viewModel()
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      val content = content()
      assertEquals(AppStatus.NotInstalled, content.status)
      assertEquals(PrimaryAction.INSTALL, content.primary)
      assertEquals(listOf(PackageTag.RECOMMENDED, null, PackageTag.NOT_FOR_DEVICE), content.packages.map { it.tag })
      assertEquals(listOf(true, true, false), content.packages.map { it.compatible })
      viewModel.onPrimary()
      assertEquals(arm64, queue.installs.single().pkg)
      assertEquals(true, queue.installs.single().recommended)
      assertEquals(PrimaryAction.CANCEL, content { it.status is AppStatus.Downloading }.primary)
      viewModel.onPrimary()
      assertEquals(listOf(1L), queue.cancelled)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `another compatible package can be installed instead, an incompatible one can't`() = runTest {
    stored()
    val viewModel = viewModel()
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      val _ = content()
      viewModel.onPackage(3)
      assertEquals(emptyList(), queue.installs)
      viewModel.onPackage(2)
      assertEquals(armv7, queue.installs.single().pkg)
      assertEquals(false, queue.installs.single().recommended)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `installed apps open, or update when the release is newer`() = runTest {
    stored()
    trackedApps.update(1) { it.copy(packageName = "com.liskovsoft.smarttubetv") }
    installedApps.install(installedApp("com.liskovsoft.smarttubetv", versionName = "1.3"))
    val viewModel = viewModel()
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      assertEquals(PrimaryAction.OPEN, content().primary)
      viewModel.onPrimary()
      assertEquals(listOf("com.liskovsoft.smarttubetv"), installedApps.launched)
      releases.store(1, release("v1.4", arm64), null, time.now)
      val update = content { it.primary == PrimaryAction.UPDATE }
      assertEquals(AppStatus.UpdateAvailable("1.3", "v1.4"), update.status)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `a finished download waits to be installed, and failures offer retry`() = runTest {
    stored()
    val viewModel = viewModel()
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      downloads.start(1, arm64, null)
      downloads.move(1, DownloadState.Done(File("a.apk"), ApkInfo("a", null, 1)))
      assertEquals(PrimaryAction.INSTALL_NOW, content { it.status == AppStatus.ReadyToInstall }.primary)
      queue.jobs.value = mapOf(1L to InstallJob.Failed(FailureReason.SignatureConflict))
      val failed = content { it.failure != null }
      assertEquals(PrimaryAction.RETRY, failed.primary)
      assertEquals(FailureReason.SignatureConflict, failed.failure)
      viewModel.onPrimary()
      assertEquals(arm64, queue.installs.single().pkg)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `without the permission the panel shows, and the install goes on once it's granted`() = runTest {
    stored()
    queue.jobs.value = mapOf(1L to InstallJob.NeedsPermission)
    permission.granted = false
    val viewModel = viewModel()
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      assertEquals(true, content().needsPermission)
      viewModel.onVisible()
      assertEquals(emptyList(), queue.installs)
      permission.granted = true
      viewModel.onVisible()
      assertEquals(arm64, queue.installs.single().pkg)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `a stale or missing release is asked for when the screen shows`() = runTest {
    val viewModel = viewModel()
    viewModel.onVisible()
    runCurrent()
    coVerify(exactly = 1) { val _ = checker.check(1) }
    releases.store(1, release("v1.3", arm64), null, time.now)
    viewModel.onVisible()
    runCurrent()
    coVerify(exactly = 1) { val _ = checker.check(1) }
    time.now += Duration.ofMinutes(11)
    viewModel.onVisible()
    runCurrent()
    coVerify(exactly = 2) { val _ = checker.check(1) }
  }

  @Test
  fun `a release without a package for this device, or a gone repository, says so`() = runTest {
    releases.store(1, release("v1.3", x86), null, time.now)
    val viewModel = viewModel()
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      val noPackage = content()
      assertEquals(AppStatus.NoPackage(null), noPackage.status)
      assertEquals(PrimaryAction.RELEASE_NOTES, noPackage.primary)
      trackedApps.update(1) { it.copy(sourceGone = true) }
      val gone = content { it.status == AppStatus.Unavailable }
      assertEquals(PrimaryAction.REMOVE, gone.primary)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `an app uninstalled outside this app shows as not installed`() = runTest {
    stored()
    trackedApps.update(1) { it.copy(packageName = "com.liskovsoft.smarttubetv") }
    installedApps.install(installedApp("com.liskovsoft.smarttubetv", versionName = "1.3"))
    val viewModel = viewModel()
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      assertEquals(AppStatus.UpToDate("1.3"), content().status)
      installedApps.uninstall("com.liskovsoft.smarttubetv")
      assertEquals(PrimaryAction.INSTALL, content { it.status == AppStatus.NotInstalled }.primary)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `release notes open on their own screen`() = runTest {
    viewModel().onReleaseNotes()
    verify { navigator.goTo(Destination.ReleaseNotes(1)) }
  }

  @Test
  fun `removing keeps the app on the TV unless asked, and leaves the list even when the uninstall is declined`() = runTest {
    stored()
    trackedApps.update(1) { it.copy(packageName = "com.liskovsoft.smarttubetv") }
    installedApps.install(installedApp("com.liskovsoft.smarttubetv", versionName = "1.3"))
    coEvery { installer.uninstall(any()) } returns UninstallOutcome.CancelledByUser
    val viewModel = viewModel()
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      val _ = content()
      viewModel.onRemove()
      assertEquals(AppDetailsViewState.Dialog.REMOVE, content { it.dialog != null }.dialog)
      viewModel.onConfirmRemove(uninstall = true)
      runCurrent()
      coVerify { val _ = installer.uninstall("com.liskovsoft.smarttubetv") }
      assertEquals(null, trackedApps.get(1))
      assertEquals(listOf(1L), queue.cancelled)
      verify { navigator.goBack() }
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `keeping the app on the TV only stops tracking it`() = runTest {
    stored()
    trackedApps.update(1) { it.copy(packageName = "com.liskovsoft.smarttubetv") }
    installedApps.install(installedApp("com.liskovsoft.smarttubetv"))
    val viewModel = viewModel()
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      val _ = content()
      viewModel.onConfirmRemove(uninstall = false)
      runCurrent()
      coVerify(exactly = 0) { val _ = installer.uninstall(any()) }
      assertEquals(null, trackedApps.get(1))
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `pre-releases are switched per app, and the app is checked again right away`() = runTest {
    releases.store(1, release("v1.3", arm64), "\"etag\"", time.now)
    val viewModel = viewModel()
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      val _ = content()
      viewModel.onTogglePrereleases()
      runCurrent()
      assertEquals(true, trackedApps.get(1)?.includePrereleases)
      assertEquals(null, releases.latest(1)?.etag)
      coVerify { val _ = checker.check(1) }
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `a remembered choice shows as the user's, and can go back to the recommended package`() = runTest {
    stored()
    trackedApps.update(1) { it.copy(packagePattern = "SmartTube_{v}_armeabi-v7a.apk") }
    val viewModel = viewModel()
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      val content = content()
      // the recommended one stays marked, so going back to it is an informed choice
      assertEquals(listOf(PackageTag.RECOMMENDED, PackageTag.YOUR_CHOICE, PackageTag.NOT_FOR_DEVICE), content.packages.map { it.tag })
      assertEquals(true, content.userChoice)
      viewModel.onPrimary()
      assertEquals(armv7, queue.installs.single().pkg)
      viewModel.onUseAutomaticChoice()
      runCurrent()
      assertEquals(null, trackedApps.get(1)?.packagePattern)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `a choice the new release doesn't have says so`() = runTest {
    stored()
    trackedApps.update(1) { it.copy(packagePattern = "SmartTube_{v}_mips.apk") }
    val viewModel = viewModel()
    backgroundScope.launchMolecule(RecompositionMode.Immediate) { viewModel.viewState() }.test {
      val content = content()
      assertEquals(true, content.choiceLost)
      assertEquals(PackageTag.RECOMMENDED, content.packages.first().tag)
      cancelAndIgnoreRemainingEvents()
    }
  }
}
