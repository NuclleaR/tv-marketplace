package com.serhii.tvmarketplace.core.common

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

class DispatcherProvider(
  val main: CoroutineContext = Dispatchers.Main,
  val io: CoroutineContext = Dispatchers.IO,
  val default: CoroutineContext = Dispatchers.Default,
)
