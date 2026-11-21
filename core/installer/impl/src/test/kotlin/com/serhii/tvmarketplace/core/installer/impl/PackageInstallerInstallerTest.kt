package com.serhii.tvmarketplace.core.installer.impl

import android.app.Application
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.serhii.tvmarketplace.core.common.DispatcherProvider
import com.serhii.tvmarketplace.core.common.rootGraph
import com.serhii.tvmarketplace.core.domain.FailureReason
import com.serhii.tvmarketplace.core.installer.ApkInfo
import com.serhii.tvmarketplace.core.installer.InstallOutcome
import com.serhii.tvmarketplace.core.installer.Installer
import com.serhii.tvmarketplace.core.installer.PackageInspector
import com.serhii.tvmarketplace.core.installer.UninstallOutcome
import com.serhii.tvmarketplace.core.testing.FakeInstallPermission
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class PackageInstallerInstallerTest {

  @get:Rule
  val folder = TemporaryFolder()

  private val application = ApplicationProvider.getApplicationContext<Application>()
  private val permission = FakeInstallPermission()
  private val apk = ApkInfo("com.example.app", "1.3.0", 130)
  private val inspector = mockk<PackageInspector> { every { inspect(any()) } returns apk }
  private val installer =
    PackageInstallerInstaller(application, permission, inspector, DispatcherProvider(Dispatchers.IO, Dispatchers.IO, Dispatchers.IO))

  // Robolectric delivers the system's own result broadcasts too; the receiver finds the installer through the graph
  @Before
  fun graph() {
    rootGraph = object : InstallerGraph {
      override val installer: Installer = this@PackageInstallerInstallerTest.installer
    }
  }

  private fun apkFile() = folder.newFile("app.apk").apply { writeBytes(ByteArray(1000)) }

  private fun status(
    request: Int,
    status: Int,
    prompt: Intent? = null,
  ) = Intent(ACTION_RESULT)
    .putExtra(EXTRA_REQUEST, request)
    .putExtra(PackageInstaller.EXTRA_STATUS, status)
    .apply { if (prompt != null) putExtra(Intent.EXTRA_INTENT, prompt) }

  // installs run on real threads here, as the session is written on one: wait for the request to reach the system.
  // The main looper stays paused, so Robolectric's own result broadcasts never arrive: the test sends the results
  private fun awaitRequest(request: Int) {
    repeat(1000) {
      if (installer.isWaiting(request)) return
      Thread.sleep(5)
    }
    error("request $request never reached the system")
  }

  @Test
  fun `without the permission nothing is started`() = runTest {
    permission.granted = false
    assertEquals(InstallOutcome.NeedsPermission, installer.install(apkFile(), null, allowSilentUpdate = false))
  }

  @Test
  fun `another app than the one installed is refused before the system sees it`() = runTest {
    assertEquals(InstallOutcome.Failed(FailureReason.DifferentApp), installer.install(apkFile(), "com.other", allowSilentUpdate = false))
  }

  @Test
  fun `a session is committed, its prompt waits for an activity, and its result comes back`() = runTest {
    val result = backgroundScope.async(Dispatchers.IO) { installer.install(apkFile(), "com.example.app", allowSilentUpdate = false) }
    awaitRequest(1)
    val prompt = Intent("com.android.packageinstaller.CONFIRM")
    installer.onResult(status(request = 1, status = PackageInstaller.STATUS_PENDING_USER_ACTION, prompt = prompt))
    assertEquals(1, installer.pendingConfirmation.value?.id)
    assertEquals(prompt.action, installer.pendingConfirmation.value?.intent?.action)
    installer.confirmationShown(1)
    assertNull(installer.pendingConfirmation.value)
    installer.onResult(status(request = 1, status = PackageInstaller.STATUS_SUCCESS))
    assertEquals(InstallOutcome.Installed("com.example.app", 130), result.await())
  }

  @Test
  fun `one install at a time`() = runTest {
    val first = backgroundScope.async(Dispatchers.IO) { installer.install(apkFile(), null, allowSilentUpdate = false) }
    awaitRequest(1)
    val second = backgroundScope.async(Dispatchers.IO) {
      installer.install(folder.newFile("b.apk").apply { writeBytes(ByteArray(10)) }, null, false)
    }
    Thread.sleep(200)
    // the second one waits for the first before it reaches the system
    assertEquals(false, installer.isWaiting(2))
    installer.onResult(status(1, PackageInstaller.STATUS_FAILURE_ABORTED))
    assertEquals(InstallOutcome.Failed(FailureReason.CancelledByUser), first.await())
    awaitRequest(2)
    installer.onResult(status(2, PackageInstaller.STATUS_SUCCESS))
    assertNotNull(second.await())
  }

  @Test
  fun `the system's statuses mean what the user is told`() {
    assertEquals(InstallOutcome.Installed("com.example.app", 130), installOutcome(PackageInstaller.STATUS_SUCCESS, null, apk))
    assertEquals(InstallOutcome.Failed(FailureReason.CancelledByUser), installOutcome(PackageInstaller.STATUS_FAILURE_ABORTED, null, apk))
    assertEquals(
      InstallOutcome.Failed(FailureReason.SignatureConflict),
      installOutcome(PackageInstaller.STATUS_FAILURE_CONFLICT, null, apk),
    )
    assertEquals(InstallOutcome.Failed(FailureReason.Incompatible), installOutcome(PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, null, apk))
    assertEquals(InstallOutcome.Failed(FailureReason.NoSpace(0)), installOutcome(PackageInstaller.STATUS_FAILURE_STORAGE, null, apk))
    assertEquals(
      InstallOutcome.Failed(FailureReason.System("blocked")),
      installOutcome(PackageInstaller.STATUS_FAILURE_BLOCKED, "blocked", apk),
    )
    assertEquals(UninstallOutcome.Uninstalled, uninstallOutcome(PackageInstaller.STATUS_SUCCESS, null))
    assertEquals(UninstallOutcome.CancelledByUser, uninstallOutcome(PackageInstaller.STATUS_FAILURE_ABORTED, null))
    assertEquals(UninstallOutcome.Failed("nope"), uninstallOutcome(PackageInstaller.STATUS_FAILURE, "nope"))
  }
}
