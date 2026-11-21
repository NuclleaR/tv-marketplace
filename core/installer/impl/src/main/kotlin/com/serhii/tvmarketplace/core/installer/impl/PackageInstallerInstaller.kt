package com.serhii.tvmarketplace.core.installer.impl

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.os.Build
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

// what the system's final status means for the user
internal fun installOutcome(
  status: Int,
  message: String?,
  apk: ApkInfo,
): InstallOutcome = when (status) {
  PackageInstaller.STATUS_SUCCESS -> InstallOutcome.Installed(apk.packageName, apk.versionCode)
  PackageInstaller.STATUS_FAILURE_ABORTED -> InstallOutcome.Failed(FailureReason.CancelledByUser)
  // also "the installed app has another signature"
  PackageInstaller.STATUS_FAILURE_CONFLICT -> InstallOutcome.Failed(FailureReason.SignatureConflict)
  PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> InstallOutcome.Failed(FailureReason.Incompatible)
  PackageInstaller.STATUS_FAILURE_STORAGE -> InstallOutcome.Failed(FailureReason.NoSpace(0))
  PackageInstaller.STATUS_FAILURE_INVALID -> InstallOutcome.Failed(FailureReason.Corrupt)
  else -> InstallOutcome.Failed(FailureReason.System(message))
}

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

// installs through system sessions, one at a time; the system's prompts are shown by whichever activity is visible
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
  private val confirmation = MutableStateFlow<PendingConfirmation?>(null)

  override val pendingConfirmation: StateFlow<PendingConfirmation?> = confirmation

  override fun confirmationShown(id: Int) {
    confirmation.update { if (it?.id == id) null else it }
  }

  override suspend fun install(
    file: File,
    expectedPackage: String?,
    allowSilentUpdate: Boolean,
  ): InstallOutcome {
    if (!permission.granted()) return InstallOutcome.NeedsPermission
    val apk = withContext(dispatchers.io) { inspector.inspect(file) } ?: return InstallOutcome.Failed(FailureReason.Corrupt)
    if (expectedPackage != null && apk.packageName != expectedPackage) return InstallOutcome.Failed(FailureReason.DifferentApp)
    return mutex.withLock {
      val installer = application.packageManager.packageInstaller
      val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
        setAppPackageName(apk.packageName)
        setSize(file.length())
        if (allowSilentUpdate && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
      }
      val sessionId = installer.createSession(params)
      val request = requests.incrementAndGet()
      val result = CompletableDeferred<Pair<Int, String?>>()
      waiting[request] = result
      try {
        withContext(dispatchers.io) {
          installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, file.length()).use { out ->
              file.inputStream().use { it.copyTo(out) }
              session.fsync(out)
            }
            session.commit(statusReceiver(request))
          }
        }
        val (status, message) = result.await()
        installOutcome(status, message, apk).also { AppLog.event("install_result", "status" to status) }
      } catch (e: CancellationException) {
        installer.abandonQuietly(sessionId)
        throw e
      } catch (e: IOException) {
        AppLog.error("install_write_failed", e)
        installer.abandonQuietly(sessionId)
        InstallOutcome.Failed(FailureReason.System(null))
      } finally {
        waiting.remove(request)
        confirmation.update { if (it?.id == request) null else it }
      }
    }
  }

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

  // for tests: whether a request has reached the system and waits for its result
  internal fun isWaiting(request: Int): Boolean = waiting.containsKey(request)

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
