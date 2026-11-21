package com.serhii.tvmarketplace.di

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.serhii.tvmarketplace.core.common.WorkerCreator

class AppWorkerFactory(creators: Set<WorkerCreator>) : WorkerFactory() {

  private val creators = creators.associateBy { it.workerClassName }

  // null lets WorkManager fall back to its own workers, such as the ones it schedules internally
  override fun createWorker(
    appContext: Context,
    workerClassName: String,
    workerParameters: WorkerParameters,
  ): ListenableWorker? = creators[workerClassName]?.create(appContext, workerParameters)
}
