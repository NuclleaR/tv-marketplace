package com.serhii.tvmarketplace.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.SingleIn

// every @ContributesTo / @ContributesBinding(AppScope::class) of a module :app depends on lands here.
// Once something needs the Application, add a factory:
// @DependencyGraph.Factory interface Factory { fun create(@Provides application: Application): AppGraph }
@SingleIn(AppScope::class)
@DependencyGraph(scope = AppScope::class)
interface AppGraph
