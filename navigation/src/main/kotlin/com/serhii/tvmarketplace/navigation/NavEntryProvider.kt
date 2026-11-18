package com.serhii.tvmarketplace.navigation

import androidx.navigation3.runtime.NavEntry
import kotlin.reflect.KClass

class NavEntryProvider<T : Destination>(
  val key: KClass<T>,
  val create: (key: T) -> NavEntry<Destination>,
)

inline fun <reified T : Destination> NavEntryProvider(noinline create: (key: T) -> NavEntry<Destination>): NavEntryProvider<T> {
  return NavEntryProvider(T::class, create)
}
