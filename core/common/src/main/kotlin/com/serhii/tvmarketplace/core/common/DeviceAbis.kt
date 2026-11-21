package com.serhii.tvmarketplace.core.common

// the architectures this device runs, its preferred one first
fun interface DeviceAbis {
  fun abis(): List<String>
}
