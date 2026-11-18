package com.serhii.tvmarketplace.navigation

import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.Inject

@Inject
class NavEntryResolver(providers: Set<NavEntryProvider<*>>) {

  private val providers = providers.associateBy { it.key }

  fun create(key: Destination): NavEntry<Destination> {
    @Suppress("UNCHECKED_CAST")
    val provider = checkNotNull(providers[key::class]) {
      "No NavEntryProvider for ${key::class.simpleName}. Does :app depend on the feature module that provides it?"
    } as NavEntryProvider<Destination>
    return provider.create(key)
  }
}
