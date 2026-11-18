package com.serhii.tvmarketplace.core.linkserver

import android.app.Application
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.serhii.tvmarketplace.core.common.AppLog
import com.serhii.tvmarketplace.core.protocol.API_VERSION
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// see specs/001-phone-link-sharing/contracts/nsd-discovery.md
const val NSD_SERVICE_TYPE = "_tvmarket._tcp."

data class Advertisement(
  val name: String,
  val type: String,
  val port: Int,
  val txt: Map<String, String>,
)

interface ServiceRegistrar {
  fun register(advertisement: Advertisement)

  fun unregister()
}

// lets the companion app find the TV by a stable id, whatever IP address the router gives it
class NsdAdvertiser(
  private val registrar: ServiceRegistrar,
  private val installId: suspend () -> String,
  private val tvName: () -> String,
) {

  fun start(
    scope: CoroutineScope,
    serverState: StateFlow<ServerState>,
  ) {
    val _ = scope.launch {
      var registered = false
      serverState.collect { state ->
        if (registered) {
          registrar.unregister()
          registered = false
        }
        if (state is ServerState.Running) {
          registrar.register(
            Advertisement(
              name = tvName(),
              type = NSD_SERVICE_TYPE,
              port = state.port,
              txt = mapOf("v" to API_VERSION.toString(), "id" to installId()),
            ),
          )
          registered = true
        }
      }
    }
  }
}

class AndroidServiceRegistrar(application: Application) : ServiceRegistrar {

  private val nsd = application.getSystemService(Context.NSD_SERVICE) as NsdManager
  private var listener: NsdManager.RegistrationListener? = null

  @Synchronized
  override fun register(advertisement: Advertisement) {
    val info = NsdServiceInfo().apply {
      serviceName = advertisement.name
      serviceType = advertisement.type
      port = advertisement.port
      advertisement.txt.forEach { (key, value) -> setAttribute(key, value) }
    }
    val new = object : NsdManager.RegistrationListener {
      override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = AppLog.event("nsd_registered")

      override fun onRegistrationFailed(
        serviceInfo: NsdServiceInfo,
        errorCode: Int,
      ) = AppLog.event("nsd_registration_failed", "code" to errorCode)

      override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit

      override fun onUnregistrationFailed(
        serviceInfo: NsdServiceInfo,
        errorCode: Int,
      ) = Unit
    }
    listener = new
    nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, new)
  }

  @Synchronized
  override fun unregister() {
    val current = listener ?: return
    listener = null
    try {
      nsd.unregisterService(current)
    } catch (e: IllegalArgumentException) {
      AppLog.error("nsd_unregister_failed", e)
    }
  }
}
