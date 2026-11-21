package com.serhii.tvmarketplace.core.common

import android.os.Build
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@BindingContainer
@ContributesTo(AppScope::class)
object CommonBindings {

  @Provides
  fun timeSource(): TimeSource = TimeSource.System

  @Provides
  fun dispatcherProvider(): DispatcherProvider = DispatcherProvider()

  @Provides
  fun deviceAbis(): DeviceAbis = DeviceAbis { Build.SUPPORTED_ABIS.toList() }

  // for work that must finish even when the screen that started it is gone, like saving a choice
  @Provides
  @SingleIn(AppScope::class)
  fun appCoroutineScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
