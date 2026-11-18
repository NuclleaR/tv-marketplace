package com.serhii.tvmarketplace.companion

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull

internal const val DISCOVERY_TIMEOUT_MS = 10_000L
private const val SERVICE_TYPE = "_tvmarket._tcp."

data class FoundTv(
  val name: String,
  // the TV's install id from the TXT record: stays the same when the router gives the TV a new address
  val id: String,
  val host: String,
  val port: Int,
)

fun interface TvBrowser {
  fun tvs(): Flow<List<FoundTv>>
}

class TvDiscovery(private val browser: TvBrowser) {

  fun tvs(): Flow<List<FoundTv>> = browser.tvs()

  suspend fun find(id: String): FoundTv? = withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) {
    browser.tvs().mapNotNull { tvs -> tvs.firstOrNull { it.id == id } }.first()
  }
}

class NsdTvBrowser(context: Context) : TvBrowser {

  private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager

  override fun tvs(): Flow<List<FoundTv>> = callbackFlow {
    val found = linkedMapOf<String, FoundTv>()
    val listener = object : NsdManager.DiscoveryListener {
      override fun onServiceFound(serviceInfo: NsdServiceInfo) {
        @Suppress("DEPRECATION") // the replacement needs API 34; this app supports older phones
        nsd.resolveService(
          serviceInfo,
          object : NsdManager.ResolveListener {
            override fun onServiceResolved(info: NsdServiceInfo) {
              val id = info.attributes["id"]?.decodeToString() ?: return

              @Suppress("DEPRECATION")
              val host = info.host?.hostAddress ?: return
              found[info.serviceName] = FoundTv(name = info.serviceName, id = id, host = host, port = info.port)
              val _ = trySend(found.values.toList())
            }

            override fun onResolveFailed(
              serviceInfo: NsdServiceInfo,
              errorCode: Int,
            ) = Unit
          },
        )
      }

      override fun onServiceLost(serviceInfo: NsdServiceInfo) {
        found.remove(serviceInfo.serviceName)
        val _ = trySend(found.values.toList())
      }

      override fun onDiscoveryStarted(serviceType: String) = Unit

      override fun onDiscoveryStopped(serviceType: String) = Unit

      override fun onStartDiscoveryFailed(
        serviceType: String,
        errorCode: Int,
      ) {
        close()
      }

      override fun onStopDiscoveryFailed(
        serviceType: String,
        errorCode: Int,
      ) = Unit
    }
    val _ = trySend(emptyList())
    nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    awaitClose {
      try {
        nsd.stopServiceDiscovery(listener)
      } catch (_: IllegalArgumentException) {
        // discovery never started
      }
    }
  }
}
