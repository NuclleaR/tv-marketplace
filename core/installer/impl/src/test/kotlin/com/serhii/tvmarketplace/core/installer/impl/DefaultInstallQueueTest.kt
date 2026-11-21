package com.serhii.tvmarketplace.core.installer.impl

import com.serhii.tvmarketplace.core.data.InstallRecord
import com.serhii.tvmarketplace.core.domain.FailureReason
import com.serhii.tvmarketplace.core.domain.GitHubRepoRef
import com.serhii.tvmarketplace.core.installer.ApkInfo
import com.serhii.tvmarketplace.core.installer.DownloadState
import com.serhii.tvmarketplace.core.installer.InstallJob
import com.serhii.tvmarketplace.core.installer.InstallOutcome
import com.serhii.tvmarketplace.core.installer.InstallRequest
import com.serhii.tvmarketplace.core.installer.Installer
import com.serhii.tvmarketplace.core.installer.UpdateBatch
import com.serhii.tvmarketplace.core.testing.FakeDownloads
import com.serhii.tvmarketplace.core.testing.FakeReleasesRepository
import com.serhii.tvmarketplace.core.testing.FakeTrackedApps
import com.serhii.tvmarketplace.core.testing.MutableTimeSource
import com.serhii.tvmarketplace.core.testing.pkg
import com.serhii.tvmarketplace.core.testing.release
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultInstallQueueTest {

  @get:Rule
  val folder = TemporaryFolder()

  private val trackedApps = FakeTrackedApps(GitHubRepoRef("o", "one"), GitHubRepoRef("o", "two"), GitHubRepoRef("o", "three"))
  private val downloads = FakeDownloads()
  private val releases = FakeReleasesRepository()
  private val time = MutableTimeSource()
  private val installer = mockk<Installer>()
  private val apk = pkg("app-release.apk", id = 9)

  private fun request(appId: Long) = InstallRequest(appId, release("v2.0", apk), apk, recommended = true)

  private fun TestScope.queue() = DefaultInstallQueue(downloads, installer, trackedApps, releases, time, backgroundScope)

  private fun TestScope.finishDownload(appId: Long) {
    runCurrent()
    downloads.move(appId, DownloadState.Running(50, 100))
    runCurrent()
    downloads.move(appId, DownloadState.Done(folder.newFile("$appId.apk"), ApkInfo("com.example.$appId", "2.0", 20)))
    runCurrent()
  }

  @Test
  fun `downloads, installs and records what was installed`() = runTest {
    coEvery { installer.install(any(), any()) } returns InstallOutcome.Installed("com.example.1", 20)
    val queue = queue()
    queue.install(request(1))
    runCurrent()
    assertEquals(listOf(1L to apk), downloads.started)
    downloads.move(1, DownloadState.Running(50, 100))
    runCurrent()
    assertEquals(InstallJob.Downloading(0.5F), queue.jobs.value[1])
    finishDownload(1)
    assertEquals(emptyMap(), queue.jobs.value)
    assertEquals("com.example.1", trackedApps.get(1)?.packageName)
    assertEquals(InstallRecord(1, "com.example.1", "v2.0", 20, "app-release.apk", time.now), releases.records.value[1])
    // the file isn't needed once installed
    assertEquals(null, downloads.downloads.value[1])
  }

  @Test
  fun `a failed download or install stays as a failure until dismissed`() = runTest {
    coEvery { installer.install(any(), any()) } returns InstallOutcome.Failed(FailureReason.SignatureConflict)
    val queue = queue()
    queue.install(request(1))
    runCurrent()
    downloads.move(1, DownloadState.Failed(FailureReason.Network))
    runCurrent()
    assertEquals(InstallJob.Failed(FailureReason.Network), queue.jobs.value[1])
    queue.dismiss(1)
    assertEquals(emptyMap(), queue.jobs.value)
    queue.install(request(1))
    finishDownload(1)
    assertEquals(InstallJob.Failed(FailureReason.SignatureConflict), queue.jobs.value[1])
  }

  @Test
  fun `without the permission the download is kept and reused once allowed`() = runTest {
    coEvery { installer.install(any(), any()) } returns InstallOutcome.NeedsPermission
    val queue = queue()
    queue.install(request(1))
    finishDownload(1)
    assertEquals(InstallJob.NeedsPermission, queue.jobs.value[1])
    coEvery { installer.install(any(), any()) } returns InstallOutcome.Installed("com.example.1", 20)
    queue.install(request(1))
    runCurrent()
    assertEquals(1, downloads.started.size)
    assertEquals(emptyMap(), queue.jobs.value)
  }

  @Test
  fun `cancel stops the download and forgets the app's job`() = runTest {
    val queue = queue()
    queue.install(request(1))
    runCurrent()
    queue.cancel(1)
    runCurrent()
    assertEquals(emptyMap(), queue.jobs.value)
    assertEquals(null, downloads.downloads.value[1])
  }

  @Test
  fun `update all installs one app after another and lists the failures`() = runTest {
    val second = CompletableDeferred<InstallOutcome>()
    coEvery { installer.install(any(), "com.example.1") } returns InstallOutcome.Installed("com.example.1", 20)
    coEvery { installer.install(any(), "com.example.2") } coAnswers { second.await() }
    coEvery { installer.install(any(), "com.example.3") } returns InstallOutcome.Installed("com.example.3", 20)
    for (id in 1L..3L) trackedApps.update(id) { it.copy(packageName = "com.example.$id") }
    val queue = queue()
    queue.updateAll(listOf(request(1), request(2), request(3)))
    finishDownload(1)
    assertEquals(UpdateBatch(total = 3, done = 1, failed = emptyMap(), stopping = false, running = true), queue.batch.value)
    finishDownload(2)
    // the third one waits for the second to finish
    assertEquals(listOf(1L, 2L), downloads.started.map { it.first })
    second.complete(InstallOutcome.Failed(FailureReason.CancelledByUser))
    finishDownload(3)
    assertEquals(
      UpdateBatch(total = 3, done = 3, failed = mapOf(2L to FailureReason.CancelledByUser), stopping = false, running = false),
      queue.batch.value,
    )
    queue.dismissBatch()
    assertEquals(null, queue.batch.value)
  }

  @Test
  fun `stop after this one finishes the current app and drops the rest`() = runTest {
    coEvery { installer.install(any(), any()) } returns InstallOutcome.Installed("com.example.1", 20)
    val queue = queue()
    queue.updateAll(listOf(request(1), request(2)))
    runCurrent()
    queue.stopAfterCurrent()
    assertEquals(true, queue.batch.value?.stopping)
    finishDownload(1)
    assertEquals(listOf(1L), downloads.started.map { it.first })
    assertEquals(false, queue.batch.value?.running)
    assertEquals(1, queue.batch.value?.done)
  }

  @Test
  fun `installing another package than the recommended one remembers it, the recommended one forgets it`() = runTest {
    coEvery { installer.install(any(), any()) } returns InstallOutcome.Installed("com.example.1", 20)
    val arm = pkg("App_2.0_armeabi-v7a.apk", id = 3)
    val queue = queue()
    queue.install(InstallRequest(1, release("v2.0", arm), arm, recommended = false))
    finishDownload(1)
    assertEquals("App_{v}_armeabi-v7a.apk", trackedApps.get(1)?.packagePattern)
    queue.install(request(1))
    runCurrent()
    downloads.move(1, DownloadState.Done(folder.newFile("again.apk"), ApkInfo("com.example.1", "2.0", 20)))
    runCurrent()
    assertEquals(null, trackedApps.get(1)?.packagePattern)
  }
}
