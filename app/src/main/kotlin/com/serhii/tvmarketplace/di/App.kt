package com.serhii.tvmarketplace.di

import android.app.Application
import com.serhii.tvmarketplace.core.common.rootGraph
import dev.zacsweers.metro.createGraph

class App : Application() {

  override fun onCreate() {
    super.onCreate()
    rootGraph = createGraph<AppGraph>()
  }
}
