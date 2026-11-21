package com.serhii.tvmarketplace.core.installer.impl

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.serhii.tvmarketplace.core.domain.InstalledApp
import com.serhii.tvmarketplace.core.installer.ApkInfo
import com.serhii.tvmarketplace.core.installer.InstallPermission
import com.serhii.tvmarketplace.core.installer.InstalledApps
import com.serhii.tvmarketplace.core.installer.PackageInspector
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.time.Instant

@ContributesBinding(AppScope::class)
@Inject
class AndroidPackageInspector(private val application: Application) : PackageInspector {

  override fun inspect(file: File): ApkInfo? {
    val info = application.packageManager.getPackageArchiveInfo(file.path, 0) ?: return null
    return ApkInfo(info.packageName, info.versionName, PackageInfoCompat.getLongVersionCode(info))
  }
}

@ContributesBinding(AppScope::class)
@Inject
class AndroidInstallPermission(private val application: Application) : InstallPermission {

  override fun granted(): Boolean = application.packageManager.canRequestPackageInstalls()

  override fun settingsIntents(): List<Intent> = listOf(
    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${application.packageName}")),
    // some TVs lack the per-app screen; the permission is then under security settings
    Intent(Settings.ACTION_SECURITY_SETTINGS),
  )
}

@ContributesBinding(AppScope::class)
@Inject
class AndroidInstalledApps(private val application: Application) : InstalledApps {

  override fun get(packageName: String): InstalledApp? {
    val manager = application.packageManager
    val info = try {
      manager.getPackageInfo(packageName, 0)
    } catch (_: PackageManager.NameNotFoundException) {
      return null
    }
    return InstalledApp(
      packageName = packageName,
      versionName = info.versionName,
      versionCode = PackageInfoCompat.getLongVersionCode(info),
      lastUpdateTime = Instant.ofEpochMilli(info.lastUpdateTime),
      installedByThisApp = installer(manager, packageName) == application.packageName,
    )
  }

  private fun installer(
    manager: PackageManager,
    packageName: String,
  ): String? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      manager.getInstallSourceInfo(packageName).installingPackageName
    } else {
      @Suppress("DEPRECATION")
      manager.getInstallerPackageName(packageName)
    }
  } catch (_: PackageManager.NameNotFoundException) {
    null
  } catch (_: IllegalArgumentException) {
    null
  }

  override fun launch(packageName: String): Boolean {
    val manager = application.packageManager
    val intent = manager.getLeanbackLaunchIntentForPackage(packageName) ?: manager.getLaunchIntentForPackage(packageName) ?: return false
    application.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    return true
  }

  // package broadcasts only reach a receiver registered at runtime, so changes made while the app wasn't running
  // show up through the refresh on every return to the foreground
  override fun changes(): Flow<Unit> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(
        context: Context,
        intent: Intent,
      ) {
        val _ = trySend(Unit)
      }
    }
    val filter = IntentFilter().apply {
      addAction(Intent.ACTION_PACKAGE_ADDED)
      addAction(Intent.ACTION_PACKAGE_REPLACED)
      addAction(Intent.ACTION_PACKAGE_REMOVED)
      addDataScheme("package")
    }
    val _ = ContextCompat.registerReceiver(application, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    val lifecycle = ProcessLifecycleOwner.get().lifecycle
    val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) trySend(Unit) }
    lifecycle.addObserver(observer)
    val _ = trySend(Unit)
    awaitClose {
      application.unregisterReceiver(receiver)
      lifecycle.removeObserver(observer)
    }
  }.flowOn(Dispatchers.Main.immediate).conflate()
}
