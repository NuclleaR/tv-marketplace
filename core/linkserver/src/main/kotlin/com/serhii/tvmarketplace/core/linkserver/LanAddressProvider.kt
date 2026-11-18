package com.serhii.tvmarketplace.core.linkserver

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.net.Inet4Address

// the IPv4 address of the TV on the home network (Wi-Fi or Ethernet), or null without one
@Inject
class LanAddressProvider(private val application: Application) {

  fun address(): Flow<String?> = callbackFlow {
    val connectivity = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val callback = object : ConnectivityManager.NetworkCallback() {
      private var capabilities: NetworkCapabilities? = null
      private var properties: LinkProperties? = null

      override fun onCapabilitiesChanged(
        network: Network,
        networkCapabilities: NetworkCapabilities,
      ) {
        capabilities = networkCapabilities
        update()
      }

      override fun onLinkPropertiesChanged(
        network: Network,
        linkProperties: LinkProperties,
      ) {
        properties = linkProperties
        update()
      }

      override fun onLost(network: Network) {
        capabilities = null
        properties = null
        val _ = trySend(null)
      }

      private fun update() {
        val caps = capabilities
        val local = caps != null &&
          (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
        val ip = properties?.linkAddresses
          ?.map { it.address }
          ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress }
          ?.hostAddress
        val _ = trySend(if (local) ip else null)
      }
    }
    val _ = trySend(null)
    connectivity.registerDefaultNetworkCallback(callback)
    awaitClose { connectivity.unregisterNetworkCallback(callback) }
  }.distinctUntilChanged()
}
