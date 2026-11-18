package com.serhii.tvmarketplace.navigation

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo

// debug builds open any back stack from an extra, so an agent or a test can jump to a screen:
// adb shell am start -n <package>/com.serhii.tvmarketplace.MainActivity --es debugBackStack '[{"type":"Home"},{"type":"AddApp"}]'
fun startDestinations(
  context: Context,
  intent: Intent,
): List<Destination> {
  val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
  val backStack = intent.getStringExtra("debugBackStack")
  if (debuggable && backStack != null) {
    return decodeBackStack(backStack).ifEmpty { listOf(Destination.Home) }
  }
  return listOf(Destination.Home)
}
