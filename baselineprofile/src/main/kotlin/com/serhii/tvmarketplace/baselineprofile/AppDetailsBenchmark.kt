package com.serhii.tvmarketplace.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// budget: opening an app's details and moving through its packages keeps jank under 5% of frames.
// Needs a tracked app on the device
@RunWith(AndroidJUnit4::class)
class AppDetailsBenchmark {

  @get:Rule
  val rule = MacrobenchmarkRule()

  @Test
  fun openDetailsAndBrowsePackages() = rule.measureRepeated(
    packageName = targetPackage,
    metrics = listOf(FrameTimingMetric()),
    compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
    startupMode = StartupMode.WARM,
    iterations = 10,
    setupBlock = {
      pressHome()
      startActivityAndWait()
    },
  ) {
    openFirstApp()
    browsePackages()
  }
}
