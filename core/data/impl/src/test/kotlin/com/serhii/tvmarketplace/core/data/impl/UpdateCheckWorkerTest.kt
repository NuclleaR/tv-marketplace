package com.serhii.tvmarketplace.core.data.impl

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.serhii.tvmarketplace.core.data.CheckResult
import com.serhii.tvmarketplace.core.data.UpdateChecker
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class UpdateCheckWorkerTest {

  private val application = ApplicationProvider.getApplicationContext<Application>()
  private val checker = mockk<UpdateChecker>()

  private fun worker() = TestListenableWorkerBuilder<UpdateCheckWorker>(application)
    .setWorkerFactory(
      object : WorkerFactory() {
        override fun createWorker(
          appContext: Context,
          workerClassName: String,
          workerParameters: WorkerParameters,
        ): ListenableWorker = UpdateCheckWorker(appContext, workerParameters, checker)
      },
    )
    .build()

  @Test
  fun `a finished or limited check succeeds, an offline one is retried`() = runTest {
    coEvery { checker.checkAll() } returns CheckResult.Done
    assertEquals(ListenableWorker.Result.success(), worker().doWork())
    coEvery { checker.checkAll() } returns CheckResult.RateLimited(Instant.EPOCH)
    assertEquals(ListenableWorker.Result.success(), worker().doWork())
    coEvery { checker.checkAll() } returns CheckResult.NetworkError
    assertEquals(ListenableWorker.Result.retry(), worker().doWork())
  }

  @Test
  fun `checks every six hours on a network, and scheduling again keeps the schedule`() {
    WorkManagerTestInitHelper.initializeTestWorkManager(application)
    val scheduler = UpdateCheckScheduler(application)
    scheduler.schedule()
    val first = WorkManager.getInstance(application).getWorkInfosForUniqueWork(UPDATE_CHECK_WORK).get().single()
    scheduler.schedule()
    val infos = WorkManager.getInstance(application).getWorkInfosForUniqueWork(UPDATE_CHECK_WORK).get()
    assertEquals(listOf(first.id), infos.map { it.id })
    assertEquals(WorkInfo.State.ENQUEUED, first.state)
    assertEquals(NetworkType.CONNECTED, first.constraints.requiredNetworkType)
    assertEquals(TimeUnit.HOURS.toMillis(6), first.periodicityInfo?.repeatIntervalMillis)
  }
}
