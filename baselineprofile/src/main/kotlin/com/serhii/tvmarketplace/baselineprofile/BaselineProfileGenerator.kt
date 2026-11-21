package com.serhii.tvmarketplace.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// ./gradlew :app:generateBaselineProfile writes app/src/release/generated/baselineProfiles/
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

  @get:Rule
  val rule = BaselineProfileRule()

  @Test
  fun startupAndAddApp() = rule.collect(packageName = targetPackage, includeInStartupProfile = true) {
    pressHome()
    startActivityAndWait()
    openAddApp()
  }

  // needs a tracked app on the device
  @Test
  fun appDetails() = rule.collect(packageName = targetPackage) {
    pressHome()
    startActivityAndWait()
    openFirstApp()
    browsePackages()
  }
}
