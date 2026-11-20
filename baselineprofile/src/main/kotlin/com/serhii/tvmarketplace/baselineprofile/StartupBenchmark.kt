package com.serhii.tvmarketplace.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// budget: cold start p90 ≤ 2.0 s with the Baseline Profile; without it shows what the profile buys
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

  @get:Rule
  val rule = MacrobenchmarkRule()

  @Test
  fun withoutProfile() = startup(CompilationMode.None())

  @Test
  fun withBaselineProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

  private fun startup(compilationMode: CompilationMode) = rule.measureRepeated(
    packageName = targetPackage,
    metrics = listOf(StartupTimingMetric()),
    compilationMode = compilationMode,
    startupMode = StartupMode.COLD,
    iterations = 10,
    setupBlock = { pressHome() },
  ) {
    startActivityAndWait()
    waitForHome()
  }
}
