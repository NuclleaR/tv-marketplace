package com.serhii.tvmarketplace.di

import android.app.Application
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.serhii.tvmarketplace.LinkServerService
import com.serhii.tvmarketplace.core.common.AppLog
import com.serhii.tvmarketplace.core.common.TimeSource
import com.serhii.tvmarketplace.core.common.rootGraph
import com.serhii.tvmarketplace.core.data.IncomingLinksRepository
import com.serhii.tvmarketplace.core.data.LinkShareSettings
import com.serhii.tvmarketplace.core.data.impl.IconBackfill
import com.serhii.tvmarketplace.core.linkserver.AndroidServiceRegistrar
import com.serhii.tvmarketplace.core.linkserver.LinkServer
import com.serhii.tvmarketplace.core.linkserver.NsdAdvertiser
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration

@ContributesTo(AppScope::class)
interface AppStartGraph {
  val linkServer: LinkServer
  val linkShareSettings: LinkShareSettings
  val incomingLinks: IncomingLinksRepository
  val timeSource: TimeSource
  val appScope: CoroutineScope
  val iconBackfill: IconBackfill
}

private val KEEP_HANDLED_LINKS: Duration = Duration.ofDays(30)

class App : Application() {

  private var receiveInBackground = false

  override fun onCreate() {
    super.onCreate()
    val graph = createGraphFactory<AppGraph.Factory>().create(this)
    rootGraph = graph
    val start = graph as AppStartGraph
    // the server lives as long as the process; the service only keeps the process alive in the background
    start.linkServer.start(start.appScope)
    NsdAdvertiser(
      registrar = AndroidServiceRegistrar(this),
      installId = start.linkShareSettings::installId,
      tvName = { Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME) ?: Build.MODEL },
    ).start(start.appScope, start.linkServer.state)
    start.appScope.launch { start.incomingLinks.purgeHandledBefore(start.timeSource.now() - KEEP_HANDLED_LINKS) }
    start.appScope.launch { start.iconBackfill.run() }
    start.appScope.launch(Dispatchers.Main) {
      start.linkShareSettings.receiveInBackground.collect { enabled ->
        receiveInBackground = enabled
        syncService()
      }
    }
    ProcessLifecycleOwner.get().lifecycle.addObserver(
      LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) syncService() },
    )
  }

  // a foreground service may only be started while the app is visible
  private fun syncService() {
    val intent = Intent(this, LinkServerService::class.java)
    if (!receiveInBackground) {
      val _ = stopService(intent)
    } else if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
      try {
        val _ = startForegroundService(intent)
      } catch (e: IllegalStateException) {
        AppLog.error("service_start_failed", e)
      }
    }
  }
}
