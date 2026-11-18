package com.serhii.tvmarketplace.companion

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

// a phone app with two screens doesn't need a DI graph: one lazily created set of services is enough
object Services {
  private val http by lazy {
    HttpClient(OkHttp) {
      expectSuccess = false
      install(HttpTimeout) { requestTimeoutMillis = 5_000 }
    }
  }

  val client: CompanionClient by lazy { CompanionClient(http) }

  fun discovery(context: Context) = TvDiscovery(NsdTvBrowser(context.applicationContext))

  fun pairing(context: Context) = PairingStore(context)
}
