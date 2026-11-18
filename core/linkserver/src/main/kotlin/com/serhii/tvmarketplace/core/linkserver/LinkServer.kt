package com.serhii.tvmarketplace.core.linkserver

import android.app.Application
import android.os.Build
import android.provider.Settings
import com.serhii.tvmarketplace.core.common.AppLog
import com.serhii.tvmarketplace.core.common.DispatcherProvider
import com.serhii.tvmarketplace.core.common.TimeSource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface ServerState {
  data object Stopped : ServerState
  data object NoNetwork : ServerState
  data class Running(
    val ip: String,
    val port: Int,
  ) : ServerState {
    val baseUrl: String get() = "http://$ip:$port"
  }
  data object Failed : ServerState
}

// a stable port keeps the pages saved on phones working; the fallbacks cover a port taken by another app
private val PORTS = (8765..8775).toList() + 0
private const val REQUESTS_PER_MINUTE = 30

@SingleIn(AppScope::class)
@Inject
class LinkServer(
  private val application: Application,
  private val sessions: SessionManager,
  private val receiver: LinkReceiver,
  private val pairing: PairingManager,
  private val lanAddress: LanAddressProvider,
  private val timeSource: TimeSource,
  private val dispatchers: DispatcherProvider,
) {

  private val mutableState = MutableStateFlow<ServerState>(ServerState.Stopped)
  val state: StateFlow<ServerState> = mutableState.asStateFlow()

  private var job: Job? = null
  private var engine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

  @Synchronized
  fun start(scope: CoroutineScope) {
    if (job?.isActive == true) return
    job = scope.launch(dispatchers.io) {
      lanAddress.address().collectLatest { ip ->
        stopEngine()
        if (ip == null) {
          mutableState.value = ServerState.NoNetwork
        } else {
          mutableState.value = bind(ip)
        }
      }
    }
  }

  @Synchronized
  fun stop() {
    job?.cancel()
    job = null
    runBlocking(dispatchers.io) { stopEngine() }
    mutableState.value = ServerState.Stopped
  }

  private suspend fun bind(ip: String): ServerState {
    var bound: ServerState.Running? = null
    val deps = LinkServerDeps(
      sessions = sessions,
      receiver = receiver,
      pages = AndroidPageAssets(application),
      allowedHosts = { bound?.let { setOf("${it.ip}:${it.port}") }.orEmpty() },
      rateLimiter = RateLimiter(timeSource, REQUESTS_PER_MINUTE),
      pairing = pairing,
      tvName = ::tvName,
    )
    for (port in PORTS) {
      val server = embeddedServer(CIO, port = port, host = ip) { linkServerModule(deps) }
      try {
        val _ = server.startSuspend(wait = false)
      } catch (e: IOException) {
        AppLog.error("server_bind_failed", e)
        server.stopSuspend(gracePeriodMillis = 0, timeoutMillis = 0)
        continue
      }
      val actualPort = server.engine.resolvedConnectors().first().port
      engine = server
      AppLog.event("server_started", "port" to actualPort)
      return ServerState.Running(ip, actualPort).also { bound = it }
    }
    return ServerState.Failed
  }

  private fun tvName(): String = Settings.Global.getString(application.contentResolver, Settings.Global.DEVICE_NAME) ?: Build.MODEL

  private suspend fun stopEngine() {
    val running = engine ?: return
    engine = null
    withContext(dispatchers.io) { running.stopSuspend(gracePeriodMillis = 200, timeoutMillis = 1000) }
  }
}

private class AndroidPageAssets(private val application: Application) : PageAssets {
  override fun read(name: String): ByteArray? = try {
    application.assets.open("web/$name").use { it.readBytes() }
  } catch (_: IOException) {
    null
  }
}
