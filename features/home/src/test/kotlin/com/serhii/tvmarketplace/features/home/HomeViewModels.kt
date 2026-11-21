package com.serhii.tvmarketplace.features.home

import com.serhii.tvmarketplace.core.data.IncomingLinksRepository
import com.serhii.tvmarketplace.core.data.ReleasesRepository
import com.serhii.tvmarketplace.core.data.TrackedAppsRepository
import com.serhii.tvmarketplace.core.data.UpdateCheckState
import com.serhii.tvmarketplace.core.data.UpdateChecker
import com.serhii.tvmarketplace.core.installer.Downloads
import com.serhii.tvmarketplace.core.installer.InstallQueue
import com.serhii.tvmarketplace.core.installer.InstalledApps
import com.serhii.tvmarketplace.core.linkserver.IncomingLinkEvents
import com.serhii.tvmarketplace.core.testing.FakeDownloads
import com.serhii.tvmarketplace.core.testing.FakeIncomingLinks
import com.serhii.tvmarketplace.core.testing.FakeInstallQueue
import com.serhii.tvmarketplace.core.testing.FakeInstalledApps
import com.serhii.tvmarketplace.core.testing.FakeReleasesRepository
import com.serhii.tvmarketplace.core.testing.FakeTrackedApps
import com.serhii.tvmarketplace.navigation.Navigator
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope

fun idleChecker(): UpdateChecker = mockk(relaxed = true) {
  every { state() } returns flowOf(UpdateCheckState(running = false, lastFinishedAt = null, resumeAt = null))
}

internal fun homeViewModel(
  trackedApps: TrackedAppsRepository = FakeTrackedApps(),
  incomingLinks: IncomingLinksRepository = FakeIncomingLinks(),
  events: IncomingLinkEvents = IncomingLinkEvents(),
  navigator: Navigator = mockk(relaxUnitFun = true),
  releases: ReleasesRepository = FakeReleasesRepository(),
  installedApps: InstalledApps = FakeInstalledApps(),
  downloads: Downloads = FakeDownloads(),
  queue: InstallQueue = FakeInstallQueue(),
  checker: UpdateChecker = idleChecker(),
  scope: CoroutineScope = TestScope(),
) = HomeViewModel(trackedApps, incomingLinks, events, navigator, releases, installedApps, downloads, queue, checker, {
  listOf("arm64-v8a")
}, scope)
