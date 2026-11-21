package com.serhii.tvmarketplace.core.installer

import com.serhii.tvmarketplace.core.domain.AppPackage
import com.serhii.tvmarketplace.core.domain.FailureReason
import kotlinx.coroutines.flow.Flow
import java.io.File

sealed interface DownloadState {
  data object Queued : DownloadState

  data class Running(
    val bytes: Long,
    val totalBytes: Long,
  ) : DownloadState {
    val fraction: Float? get() = if (totalBytes > 0) (bytes.toFloat() / totalBytes).coerceIn(0F, 1F) else null
  }

  data object Verifying : DownloadState

  // complete and checked: the file is the package it claims to be
  data class Done(
    val file: File,
    val apk: ApkInfo,
  ) : DownloadState

  data class Failed(val reason: FailureReason) : DownloadState
}

data class Download(
  val appId: Long,
  val packageId: Long,
  val state: DownloadState,
)

// downloads keep running when the screen or the app is left; at most one per tracked app
interface Downloads {
  // expectedPackage: the app's known package name, a package with another one fails as DifferentApp
  fun start(
    appId: Long,
    pkg: AppPackage,
    expectedPackage: String?,
  )

  // stops a running download and deletes what it fetched; also forgets a finished or failed one
  fun cancel(appId: Long)

  fun observe(appId: Long): Flow<Download?>

  fun observeAll(): Flow<Map<Long, Download>>
}
