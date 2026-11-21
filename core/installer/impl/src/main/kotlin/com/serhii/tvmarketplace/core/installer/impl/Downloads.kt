package com.serhii.tvmarketplace.core.installer.impl

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.StatFs
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.serhii.tvmarketplace.core.common.AppLog
import com.serhii.tvmarketplace.core.common.WorkerCreator
import com.serhii.tvmarketplace.core.domain.AppPackage
import com.serhii.tvmarketplace.core.domain.FailureReason
import com.serhii.tvmarketplace.core.installer.ApkInfo
import com.serhii.tvmarketplace.core.installer.Download
import com.serhii.tvmarketplace.core.installer.DownloadState
import com.serhii.tvmarketplace.core.installer.Downloads
import com.serhii.tvmarketplace.core.installer.PackageInspector
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File
import com.serhii.tvmarketplace.core.strings.R as StringsR

private const val TAG_DOWNLOAD = "download"
private const val TAG_APP = "app:"
private const val CHANNEL_ID = "downloads"

private const val KEY_APP_ID = "appId"
private const val KEY_PACKAGE_ID = "packageId"
private const val KEY_FILE_NAME = "fileName"
private const val KEY_SIZE = "size"
private const val KEY_URL = "url"
private const val KEY_SHA256 = "sha256"
private const val KEY_EXPECTED_PACKAGE = "expectedPackage"
private const val KEY_BYTES = "bytes"
private const val KEY_TOTAL = "total"
private const val KEY_VERIFYING = "verifying"
private const val KEY_FILE = "file"
private const val KEY_PACKAGE_NAME = "packageName"
private const val KEY_VERSION_NAME = "versionName"
private const val KEY_VERSION_CODE = "versionCode"
private const val KEY_REASON = "reason"
private const val KEY_NEEDED = "needed"

private fun workName(appId: Long) = "download-$appId"

@BindingContainer
@ContributesTo(AppScope::class)
object DownloadBindings {

  // no backup: a half-downloaded APK is worthless on another device
  @Provides
  @SingleIn(AppScope::class)
  fun apkDownloader(
    application: Application,
    client: HttpClient,
    inspector: PackageInspector,
  ): ApkDownloader = ApkDownloader(
    client = client,
    inspector = inspector,
    directory = File(application.noBackupFilesDir, "downloads"),
    freeBytes = { directory -> StatFs(directory.path).availableBytes },
  )
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class WorkManagerDownloads(
  private val application: Application,
  private val downloader: ApkDownloader,
) : Downloads {

  private val workManager get() = WorkManager.getInstance(application)

  // a finished download's work stays in WorkManager's history for a while; once forgotten it's hidden, until the
  // app's next download
  private val forgotten = MutableStateFlow<Set<Long>>(emptySet())

  override fun start(
    appId: Long,
    pkg: AppPackage,
    expectedPackage: String?,
  ) {
    val request = OneTimeWorkRequestBuilder<DownloadWorker>()
      .setInputData(
        workDataOf(
          KEY_APP_ID to appId,
          KEY_PACKAGE_ID to pkg.id,
          KEY_FILE_NAME to pkg.fileName,
          KEY_SIZE to pkg.sizeBytes,
          KEY_URL to pkg.downloadUrl,
          KEY_SHA256 to pkg.sha256,
          KEY_EXPECTED_PACKAGE to expectedPackage,
        ),
      )
      .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
      .addTag(TAG_DOWNLOAD)
      .addTag(TAG_APP + appId)
      .build()
    forgotten.value -= appId
    val _ = workManager.enqueueUniqueWork(workName(appId), ExistingWorkPolicy.REPLACE, request)
  }

  override fun cancel(appId: Long) {
    val _ = workManager.cancelUniqueWork(workName(appId))
    downloader.deleteFiles(appId)
    forgotten.value += appId
  }

  override fun observe(appId: Long): Flow<Download?> = combine(
    workManager.getWorkInfosForUniqueWorkFlow(workName(appId)),
    forgotten,
  ) { infos, hidden -> infos.lastOrNull()?.toDownload(hidden) }.distinctUntilChanged()

  override fun observeAll(): Flow<Map<Long, Download>> = combine(
    workManager.getWorkInfosByTagFlow(TAG_DOWNLOAD),
    forgotten,
  ) { infos, hidden ->
    infos.mapNotNull { it.toDownload(hidden) }.associateBy { it.appId }
  }.distinctUntilChanged()

  private fun WorkInfo.toDownload(forgotten: Set<Long>): Download? {
    val appId = tags.firstOrNull { it.startsWith(TAG_APP) }?.removePrefix(TAG_APP)?.toLongOrNull() ?: return null
    if (state.isFinished && appId in forgotten) return null
    val packageId = outputData.getLong(KEY_PACKAGE_ID, progress.getLong(KEY_PACKAGE_ID, -1))
    val state = when (state) {
      WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadState.Queued
      WorkInfo.State.RUNNING -> if (progress.getBoolean(KEY_VERIFYING, false)) {
        DownloadState.Verifying
      } else {
        DownloadState.Running(progress.getLong(KEY_BYTES, 0), progress.getLong(KEY_TOTAL, 0))
      }
      WorkInfo.State.SUCCEEDED -> {
        val file = outputData.getString(KEY_FILE)?.let(::File)?.takeIf { it.exists() } ?: return null
        DownloadState.Done(
          file,
          ApkInfo(
            packageName = outputData.getString(KEY_PACKAGE_NAME) ?: return null,
            versionName = outputData.getString(KEY_VERSION_NAME),
            versionCode = outputData.getLong(KEY_VERSION_CODE, 0),
          ),
        )
      }
      WorkInfo.State.FAILED -> DownloadState.Failed(outputData.failureReason())
      WorkInfo.State.CANCELLED -> return null
    }
    return Download(appId = appId, packageId = packageId, state = state)
  }
}

private fun Data.failureReason(): FailureReason = when (getString(KEY_REASON)) {
  "no_space" -> FailureReason.NoSpace(getLong(KEY_NEEDED, 0))
  "corrupt" -> FailureReason.Corrupt
  "different_app" -> FailureReason.DifferentApp
  "network" -> FailureReason.Network
  else -> FailureReason.System(null)
}

private fun FailureReason.toData(): Data = when (this) {
  is FailureReason.NoSpace -> workDataOf(KEY_REASON to "no_space", KEY_NEEDED to neededBytes)
  FailureReason.Corrupt -> workDataOf(KEY_REASON to "corrupt")
  FailureReason.DifferentApp -> workDataOf(KEY_REASON to "different_app")
  FailureReason.Network -> workDataOf(KEY_REASON to "network")
  else -> workDataOf(KEY_REASON to "system")
}

// runs as foreground work with a notification, so the download survives the user leaving the app
class DownloadWorker(
  context: Context,
  params: WorkerParameters,
  private val downloader: ApkDownloader,
) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result {
    val appId = inputData.getLong(KEY_APP_ID, -1)
    val pkg = AppPackage(
      id = inputData.getLong(KEY_PACKAGE_ID, -1),
      fileName = inputData.getString(KEY_FILE_NAME).orEmpty(),
      sizeBytes = inputData.getLong(KEY_SIZE, 0),
      downloadUrl = inputData.getString(KEY_URL).orEmpty(),
      sha256 = inputData.getString(KEY_SHA256),
    )
    goForeground(pkg.fileName)
    val outcome = downloader.download(appId, pkg, inputData.getString(KEY_EXPECTED_PACKAGE)) { bytes, total ->
      setProgress(
        workDataOf(
          KEY_PACKAGE_ID to pkg.id,
          KEY_BYTES to bytes,
          KEY_TOTAL to total,
          KEY_VERIFYING to (bytes >= total && total > 0),
        ),
      )
    }
    return when (outcome) {
      is ApkDownloader.Outcome.Done -> {
        AppLog.event("download_done", "bytes" to outcome.file.length())
        Result.success(
          workDataOf(
            KEY_PACKAGE_ID to pkg.id,
            KEY_FILE to outcome.file.path,
            KEY_PACKAGE_NAME to outcome.apk.packageName,
            KEY_VERSION_NAME to outcome.apk.versionName,
            KEY_VERSION_CODE to outcome.apk.versionCode,
          ),
        )
      }
      is ApkDownloader.Outcome.Failed -> {
        AppLog.event("download_failed", "reason" to outcome.reason.code())
        Result.failure(Data.Builder().putAll(outcome.reason.toData()).putLong(KEY_PACKAGE_ID, pkg.id).build())
      }
    }
  }

  private suspend fun goForeground(fileName: String) {
    val manager = applicationContext.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
      NotificationChannel(
        CHANNEL_ID,
        applicationContext.getString(StringsR.string.notification_downloads),
        NotificationManager.IMPORTANCE_LOW,
      ),
    )
    val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setContentTitle(applicationContext.getString(StringsR.string.notification_download, fileName))
      .setOngoing(true)
      .setProgress(0, 0, true)
      .build()
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ForegroundInfo(id.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
      ForegroundInfo(id.hashCode(), notification)
    }
    try {
      setForeground(info)
    } catch (e: IllegalStateException) {
      // the system refuses foreground work started from the background; the download still runs as normal work
      AppLog.error("download_foreground_refused", e)
    }
  }
}

// numbers for logs: AppLog takes no free text
internal fun FailureReason.code(): Int = when (this) {
  FailureReason.Network -> 1
  is FailureReason.RateLimited -> 2
  is FailureReason.NoSpace -> 3
  FailureReason.Corrupt -> 4
  FailureReason.DifferentApp -> 5
  FailureReason.SignatureConflict -> 6
  FailureReason.Incompatible -> 7
  FailureReason.CancelledByUser -> 8
  is FailureReason.System -> 9
}

@ContributesIntoSet(AppScope::class)
@Inject
class DownloadWorkerCreator(private val downloader: ApkDownloader) : WorkerCreator {
  override val workerClassName: String = DownloadWorker::class.java.name

  override fun create(
    context: Context,
    params: WorkerParameters,
  ): ListenableWorker = DownloadWorker(context, params, downloader)
}
