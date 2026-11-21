package com.serhii.tvmarketplace.core.installer.impl

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.serhii.tvmarketplace.core.domain.InstalledApp
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class AndroidInstalledAppsTest {

  private val application = ApplicationProvider.getApplicationContext<Application>()
  private val installedApps = AndroidInstalledApps(application)

  private fun install(
    packageName: String,
    versionName: String,
    versionCode: Long,
  ) {
    shadowOf(application.packageManager).installPackage(
      PackageInfo().apply {
        this.packageName = packageName
        this.versionName = versionName
        longVersionCode = versionCode
        lastUpdateTime = 1_700_000_000_000
        applicationInfo = ApplicationInfo().apply { this.packageName = packageName }
      },
    )
  }

  @Test
  fun `an installed app has its version, an unknown one is not installed`() {
    install("com.liskovsoft.smarttubetv", "32.80", 3280)
    assertEquals(
      InstalledApp("com.liskovsoft.smarttubetv", "32.80", 3280, Instant.ofEpochMilli(1_700_000_000_000), installedByThisApp = false),
      installedApps.get("com.liskovsoft.smarttubetv"),
    )
    assertNull(installedApps.get("com.not.installed"))
  }

  @Test
  fun `installs, updates and removals by anyone are noticed`() = runTest {
    installedApps.changes().test {
      ShadowLooper.idleMainLooper()
      awaitItem()
      application.sendBroadcast(Intent(Intent.ACTION_PACKAGE_REMOVED, Uri.parse("package:com.example")))
      ShadowLooper.idleMainLooper()
      awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }
}
