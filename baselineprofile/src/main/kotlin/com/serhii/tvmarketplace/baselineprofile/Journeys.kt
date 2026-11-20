package com.serhii.tvmarketplace.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

private const val TIMEOUT_MS = 10_000L

// the baselineprofile plugin passes the app's id, which depends on the variant under test
val targetPackage: String =
  InstrumentationRegistry.getArguments().getString("targetAppId")
    ?: error("targetAppId is missing: run through the baselineprofile Gradle tasks")

fun MacrobenchmarkScope.waitForHome() {
  check(device.wait(Until.hasObject(By.text("Add app")), TIMEOUT_MS)) { "Home didn't show" }
}

// Home focuses Add app on start; a TV is driven with the remote, so press OK instead of tapping
fun MacrobenchmarkScope.openAddApp() {
  waitForHome()
  device.pressDPadCenter()
  check(device.wait(Until.hasObject(By.desc("QR code to send a link from your phone")), TIMEOUT_MS)) {
    "Add app didn't show the QR code"
  }
}
