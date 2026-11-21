package com.serhii.tvmarketplace.core.common

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds

// a module that has a worker contributes one of these @ContributesIntoSet(AppScope::class); :app builds the
// WorkManager factory from all of them, so workers get their dependencies from the graph
interface WorkerCreator {
  val workerClassName: String

  fun create(
    context: Context,
    params: WorkerParameters,
  ): ListenableWorker
}

@ContributesTo(AppScope::class)
interface WorkerCreatorBindings {
  // empty until a module contributes a worker
  @Multibinds(allowEmpty = true)
  fun workerCreators(): Set<WorkerCreator>
}
