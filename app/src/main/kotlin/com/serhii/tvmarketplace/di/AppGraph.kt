package com.serhii.tvmarketplace.di

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

// every @ContributesTo / @ContributesBinding(AppScope::class) of a module :app depends on lands here
@SingleIn(AppScope::class)
@DependencyGraph(scope = AppScope::class)
interface AppGraph {

  @DependencyGraph.Factory
  fun interface Factory {
    fun create(@Provides application: Application): AppGraph
  }
}
