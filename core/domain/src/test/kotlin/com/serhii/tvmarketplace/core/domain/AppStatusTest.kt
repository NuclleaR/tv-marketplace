package com.serhii.tvmarketplace.core.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class AppStatusTest {

  private val release = Release("v2.0", null, Instant.EPOCH, prerelease = false, notesHtml = null, packages = emptyList())
  private val installed = InstalledApp("com.example", "1.0", 1, Instant.EPOCH, installedByThisApp = true)

  private fun status(
    sourceGone: Boolean = false,
    activity: AppStatus.Activity? = null,
    latest: Release? = release,
    hasCompatiblePackage: Boolean = true,
    installed: InstalledApp? = null,
    newer: Boolean = true,
  ) = AppStatus.of(sourceGone, activity, latest, hasCompatiblePackage, installed) { _, _ -> newer }

  @Test
  fun `a gone source wins over everything`() {
    assertEquals(AppStatus.Unavailable, status(sourceGone = true, activity = AppStatus.Activity.Installing, installed = installed))
  }

  @Test
  fun `a running download comes before the installed state`() {
    assertEquals(AppStatus.Downloading(0.5F), status(activity = AppStatus.Activity.Downloading(0.5F), installed = installed))
    assertEquals(AppStatus.Installing, status(activity = AppStatus.Activity.Installing, installed = installed))
    assertEquals(AppStatus.ReadyToInstall, status(activity = AppStatus.Activity.ReadyToInstall, installed = installed))
  }

  @Test
  fun `not installed with a package to install`() {
    assertEquals(AppStatus.NotInstalled, status())
  }

  @Test
  fun `no compatible package or no release at all`() {
    assertEquals(AppStatus.NoPackage(null), status(hasCompatiblePackage = false))
    assertEquals(AppStatus.NoRelease, status(latest = null))
    // installed, and the newer release has nothing for this device: not offered as an update
    assertEquals(AppStatus.NoPackage("1.0"), status(hasCompatiblePackage = false, installed = installed))
  }

  @Test
  fun `installed apps are up to date or have an update`() {
    assertEquals(AppStatus.UpdateAvailable("1.0", "v2.0"), status(installed = installed))
    assertEquals(AppStatus.UpToDate("1.0"), status(installed = installed, newer = false))
    assertEquals(AppStatus.UpToDate("1.0"), status(installed = installed, latest = null))
    // an older release with no package for this device doesn't matter once it's not newer
    assertEquals(AppStatus.UpToDate("1.0"), status(installed = installed, newer = false, hasCompatiblePackage = false))
  }
}
