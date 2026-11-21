package com.serhii.tvmarketplace.core.installer.impl

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
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
import kotlin.test.assertTrue

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
    installer.shareFile = { Uri.parse("content://com.example.apk-files/install/${it.name}") }
    rootGraph = object : InstallerGraph {
      override val installer: Installer = this@PackageInstallerInstallerTest.installer
    }
  }

  private fun apkFile() = folder.newFile("app.apk").apply { writeBytes(ByteArray(1000)) }

  // installs run on real threads here, as the APK is copied on one: wait for the install to reach the system installer.
  private fun awaitPrompt(request: Int) {
    repeat(1000) {
      if (installer.isPrompting(request) && installer.pendingConfirmation.value?.id == request) return
      Thread.sleep(5)
    }
    error("request $request never reached the system installer")
  }

  @Test
  fun `without the permission nothing is started`() = runTest {
    permission.granted = false
    assertEquals(InstallOutcome.NeedsPermission, installer.install(apkFile(), null))
  }

  @Test
  fun `another app than the one installed is refused before the system sees it`() = runTest {
    assertEquals(InstallOutcome.Failed(FailureReason.DifferentApp), installer.install(apkFile(), "com.other"))
  }

  @Test
  fun `the system installer's screens are opened for their result, and the result comes back`() = runTest {
    val result = backgroundScope.async(Dispatchers.IO) { installer.install(apkFile(), "com.example.app") }
    awaitPrompt(1)
    val prompt = assertNotNull(installer.pendingConfirmation.value)
    assertEquals(1, prompt.id)
    assertTrue(prompt.returnsResult)
    @Suppress("DEPRECATION")
    assertEquals(Intent.ACTION_INSTALL_PACKAGE, prompt.intent.action)
    assertTrue(prompt.intent.getBooleanExtra(Intent.EXTRA_RETURN_RESULT, false))
    assertTrue(prompt.intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    assertEquals("content", prompt.intent.data?.scheme)
    installer.confirmationShown(1)
    assertNull(installer.pendingConfirmation.value)
    installer.promptResult(1, Activity.RESULT_OK, null)
    assertEquals(InstallOutcome.Installed("com.example.app", 130), result.await())
  }

  @Test
  fun `one install at a time`() = runTest {
    val first = backgroundScope.async(Dispatchers.IO) { installer.install(apkFile(), null) }
    awaitPrompt(1)
    val second = backgroundScope.async(Dispatchers.IO) {
      installer.install(folder.newFile("b.apk").apply { writeBytes(ByteArray(10)) }, null)
    }
    Thread.sleep(200)
    // the second one waits for the first before it reaches the system
    assertEquals(false, installer.isPrompting(2))
    installer.promptResult(1, Activity.RESULT_CANCELED, null)
    assertEquals(InstallOutcome.Failed(FailureReason.CancelledByUser), first.await())
    awaitPrompt(2)
    installer.promptResult(2, Activity.RESULT_OK, null)
    assertNotNull(second.await())
  }

  private fun failed(installResult: Int) = installOutcome(Activity.RESULT_FIRST_USER, installResult, apk)

  @Test
  fun `the system installer's results mean what the user is told`() {
    assertEquals(InstallOutcome.Installed("com.example.app", 130), installOutcome(Activity.RESULT_OK, 0, apk))
    assertEquals(InstallOutcome.Failed(FailureReason.CancelledByUser), installOutcome(Activity.RESULT_CANCELED, 0, apk))
    assertEquals(InstallOutcome.Failed(FailureReason.NewerInstalled), failed(-25))
    assertEquals(InstallOutcome.Failed(FailureReason.SignatureConflict), failed(-7))
    assertEquals(InstallOutcome.Failed(FailureReason.NoSpace(0)), failed(-4))
    assertEquals(InstallOutcome.Failed(FailureReason.Incompatible), failed(-16))
    assertEquals(InstallOutcome.Failed(FailureReason.Corrupt), failed(-103))
    assertEquals(InstallOutcome.Failed(FailureReason.System("-200")), failed(-200))
    assertEquals(UninstallOutcome.Uninstalled, uninstallOutcome(PackageInstaller.STATUS_SUCCESS, null))
    assertEquals(UninstallOutcome.CancelledByUser, uninstallOutcome(PackageInstaller.STATUS_FAILURE_ABORTED, null))
    assertEquals(UninstallOutcome.Failed("nope"), uninstallOutcome(PackageInstaller.STATUS_FAILURE, "nope"))
  }
}
