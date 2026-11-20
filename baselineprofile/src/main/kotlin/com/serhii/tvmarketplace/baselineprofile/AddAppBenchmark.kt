package com.serhii.tvmarketplace.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// budget: the QR is on screen ≤ 500 ms after opening Add app (AddApp:qrShown), jank < 5% of frames
@RunWith(AndroidJUnit4::class)
class AddAppBenchmark {

  @get:Rule
  val rule = MacrobenchmarkRule()

  @OptIn(ExperimentalMetricApi::class)
  @Test
  fun openAddApp() = rule.measureRepeated(
    packageName = targetPackage,
    metrics = listOf(TraceSectionMetric("AddApp:qrShown"), FrameTimingMetric()),
    compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
    startupMode = StartupMode.COLD,
    iterations = 10,
    setupBlock = {
      pressHome()
      startActivityAndWait()
    },
  ) {
    openAddApp()
  }
}
