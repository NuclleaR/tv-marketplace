package com.serhii.tvmarketplace.core.data.impl

import android.app.Application
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.serhii.tvmarketplace.core.common.WorkerCreator
import com.serhii.tvmarketplace.core.data.CheckResult
import com.serhii.tvmarketplace.core.data.UpdateChecker
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import java.util.concurrent.TimeUnit

internal const val UPDATE_CHECK_WORK = "update-check"
internal const val CHECK_INTERVAL_HOURS = 6L

// checks for updates in the background; it only stores what it finds, installing is always the user's choice
class UpdateCheckWorker(
  context: Context,
  params: WorkerParameters,
  private val checker: UpdateChecker,
) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result = when (checker.checkAll()) {
    CheckResult.Done -> Result.success()
    // the next period comes after GitHub's limit has reset; retrying sooner would only be refused
    is CheckResult.RateLimited -> Result.success()
    CheckResult.NetworkError -> Result.retry()
  }
}

@ContributesIntoSet(AppScope::class)
@Inject
class UpdateCheckWorkerCreator(private val checker: UpdateChecker) : WorkerCreator {
  override val workerClassName: String = UpdateCheckWorker::class.java.name

  override fun create(
    context: Context,
    params: WorkerParameters,
  ): ListenableWorker = UpdateCheckWorker(context, params, checker)
}

@Inject
class UpdateCheckScheduler(private val application: Application) {

  // KEEP: starting the app again doesn't move the next check
  fun schedule() {
    val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(CHECK_INTERVAL_HOURS, TimeUnit.HOURS)
      .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
      .build()
    val _ = WorkManager.getInstance(application).enqueueUniquePeriodicWork(UPDATE_CHECK_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
  }
}
