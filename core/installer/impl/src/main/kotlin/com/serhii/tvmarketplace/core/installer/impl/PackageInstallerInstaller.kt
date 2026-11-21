package com.serhii.tvmarketplace.core.installer.impl

import android.app.Activity
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import com.serhii.tvmarketplace.core.common.AppLog
import com.serhii.tvmarketplace.core.common.DispatcherProvider
import com.serhii.tvmarketplace.core.common.rootGraphAs
import com.serhii.tvmarketplace.core.domain.FailureReason
import com.serhii.tvmarketplace.core.installer.ApkInfo
import com.serhii.tvmarketplace.core.installer.InstallOutcome
import com.serhii.tvmarketplace.core.installer.InstallPermission
import com.serhii.tvmarketplace.core.installer.Installer
import com.serhii.tvmarketplace.core.installer.PackageInspector
import com.serhii.tvmarketplace.core.installer.PendingConfirmation
import com.serhii.tvmarketplace.core.installer.UninstallOutcome
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal const val ACTION_RESULT = "com.serhii.tvmarketplace.INSTALL_RESULT"
internal const val EXTRA_REQUEST = "request"

// Intent.EXTRA_INSTALL_RESULT, hidden
internal const val EXTRA_INSTALL_RESULT = "android.intent.extra.INSTALL_RESULT"

// the system installer's result: RESULT_OK, RESULT_CANCELED when the user backed out, otherwise RESULT_FIRST_USER with
// PackageManager's INSTALL_FAILED_* / INSTALL_PARSE_FAILED_* code in EXTRA_INSTALL_RESULT
internal fun installOutcome(
  resultCode: Int,
  installResult: Int,
  apk: ApkInfo,
): InstallOutcome = when {
  resultCode == Activity.RESULT_OK -> InstallOutcome.Installed(apk.packageName, apk.versionCode)
  resultCode == Activity.RESULT_CANCELED && installResult == 0 -> InstallOutcome.Failed(FailureReason.CancelledByUser)
  installResult == INSTALL_FAILED_VERSION_DOWNGRADE -> InstallOutcome.Failed(FailureReason.NewerInstalled)
  // also "the installed app has another signature"
  installResult == INSTALL_FAILED_UPDATE_INCOMPATIBLE -> InstallOutcome.Failed(FailureReason.SignatureConflict)
  installResult == INSTALL_FAILED_INSUFFICIENT_STORAGE -> InstallOutcome.Failed(FailureReason.NoSpace(0))
  installResult in INCOMPATIBLE -> InstallOutcome.Failed(FailureReason.Incompatible)
  installResult == INSTALL_FAILED_INVALID_APK || installResult in INSTALL_PARSE_FAILED -> InstallOutcome.Failed(FailureReason.Corrupt)
  installResult == INSTALL_FAILED_ABORTED -> InstallOutcome.Failed(FailureReason.CancelledByUser)
  else -> InstallOutcome.Failed(FailureReason.System(installResult.takeIf { it != 0 }?.toString()))
}

// PackageManager's hidden INSTALL_* codes
private const val INSTALL_FAILED_INVALID_APK = -2
private const val INSTALL_FAILED_INSUFFICIENT_STORAGE = -4
private const val INSTALL_FAILED_UPDATE_INCOMPATIBLE = -7
private const val INSTALL_FAILED_VERSION_DOWNGRADE = -25
private const val INSTALL_FAILED_ABORTED = -115

// older SDK, newer SDK, missing feature, CPU ABI, missing shared library
private val INCOMPATIBLE = setOf(-12, -14, -16, -17, -9)
private val INSTALL_PARSE_FAILED = -125..-100

private const val APK_MIME = "application/vnd.android.package-archive"

internal fun uninstallOutcome(
  status: Int,
  message: String?,
): UninstallOutcome = when (status) {
  PackageInstaller.STATUS_SUCCESS -> UninstallOutcome.Uninstalled
  PackageInstaller.STATUS_FAILURE_ABORTED -> UninstallOutcome.CancelledByUser
  else -> UninstallOutcome.Failed(message)
}

@ContributesTo(AppScope::class)
interface InstallerGraph {
  val installer: Installer
}

// installs through the system installer's screens and uninstalls through system sessions, one at a time; both are
// started by whichever activity is visible
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class PackageInstallerInstaller(
  private val application: Application,
  private val permission: InstallPermission,
  private val inspector: PackageInspector,
  private val dispatchers: DispatcherProvider,
) : Installer {

  private val mutex = Mutex()
  private val requests = AtomicInteger()
  private val waiting = ConcurrentHashMap<Int, CompletableDeferred<Pair<Int, String?>>>()

  // a link the system installer may read; tests replace it, as FileProvider caches its folders across Robolectric's
  internal var shareFile: (File) -> Uri = { FileProvider.getUriForFile(application, "${application.packageName}.apk-files", it) }
  private val prompts = ConcurrentHashMap<Int, CompletableDeferred<Pair<Int, Int>>>()
  private val confirmation = MutableStateFlow<PendingConfirmation?>(null)

  override val pendingConfirmation: StateFlow<PendingConfirmation?> = confirmation

  override fun confirmationShown(id: Int) {
    confirmation.update { if (it?.id == id) null else it }
  }

  override suspend fun install(
    file: File,
    expectedPackage: String?,
  ): InstallOutcome {
    if (!permission.granted()) return InstallOutcome.NeedsPermission
    val apk = withContext(dispatchers.io) { inspector.inspect(file) } ?: return InstallOutcome.Failed(FailureReason.Corrupt)
    if (expectedPackage != null && apk.packageName != expectedPackage) return InstallOutcome.Failed(FailureReason.DifferentApp)
    return mutex.withLock {
      val request = requests.incrementAndGet()
      val result = CompletableDeferred<Pair<Int, Int>>()
      // the system installer reads the APK through a link to a copy, as downloads live where no provider can share
      val shared = File(application.cacheDir, "install/$request.apk")
      try {
        withContext(dispatchers.io) {
          shared.parentFile?.mkdirs()
          file.copyTo(shared, overwrite = true)
        }
        val uri = shareFile(shared)

        @Suppress("DEPRECATION") // still the only way to get the system installer's own screens, progress included
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
          .setDataAndType(uri, APK_MIME)
          .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          .putExtra(Intent.EXTRA_RETURN_RESULT, true)
          .putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, application.packageName)
        prompts[request] = result
        confirmation.value = PendingConfirmation(request, intent, returnsResult = true)
        val (resultCode, installResult) = result.await()
        installOutcome(resultCode, installResult, apk).also {
          AppLog.event("install_result", "code" to resultCode, "result" to installResult)
        }
      } catch (e: IOException) {
        AppLog.error("install_copy_failed", e)
        InstallOutcome.Failed(FailureReason.NoSpace(0))
      } catch (e: IllegalArgumentException) {
        // the provider doesn't share the copy's folder
        AppLog.error("install_share_failed", e)
        InstallOutcome.Failed(FailureReason.System(null))
      } finally {
        prompts.remove(request)
        confirmation.update { if (it?.id == request) null else it }
        val _ = shared.delete()
      }
    }
  }

  override fun promptResult(
    id: Int,
    resultCode: Int,
    data: Intent?,
  ) {
    val installResult = data?.getIntExtra(EXTRA_INSTALL_RESULT, 0) ?: 0
    val _ = prompts[id]?.complete(resultCode to installResult)
  }

  // for tests: whether an install waits for the system installer
  internal fun isPrompting(request: Int): Boolean = prompts.containsKey(request)

  override suspend fun uninstall(packageName: String): UninstallOutcome = mutex.withLock {
    val request = requests.incrementAndGet()
    val result = CompletableDeferred<Pair<Int, String?>>()
    waiting[request] = result
    try {
      application.packageManager.packageInstaller.uninstall(packageName, statusReceiver(request))
      val (status, message) = result.await()
      uninstallOutcome(status, message)
    } finally {
      waiting.remove(request)
      confirmation.update { if (it?.id == request) null else it }
    }
  }

  // the receiver hands every status to this
  internal fun onResult(intent: Intent) {
    val request = intent.getIntExtra(EXTRA_REQUEST, -1)
    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
    if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
      val prompt = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
      if (prompt != null) confirmation.value = PendingConfirmation(request, prompt)
      return
    }
    val _ = waiting[request]?.complete(status to intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
  }

  // mutable: the system adds the status extras to it
  private fun statusReceiver(request: Int): IntentSender {
    val intent = Intent(application, InstallResultReceiver::class.java)
      .setAction(ACTION_RESULT)
      .setPackage(application.packageName)
      .putExtra(EXTRA_REQUEST, request)
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
    return PendingIntent.getBroadcast(application, request, intent, flags).intentSender
  }
}

internal fun PackageInstaller.abandonQuietly(sessionId: Int) {
  try {
    abandonSession(sessionId)
  } catch (_: SecurityException) {
    // already finished or gone
  }
}

class InstallResultReceiver : BroadcastReceiver() {
  override fun onReceive(
    context: Context,
    intent: Intent,
  ) {
    if (intent.action != ACTION_RESULT) return
    (rootGraphAs<InstallerGraph>().installer as? PackageInstallerInstaller)?.onResult(intent)
  }
}
