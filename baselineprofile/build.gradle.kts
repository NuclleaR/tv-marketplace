plugins {
  id("convention.ktlint")
  alias(libs.plugins.android.test)
  alias(libs.plugins.baselineprofile)
}

android {
  namespace = "com.serhii.tvmarketplace.baselineprofile"
  compileSdk = libs.versions.sdk.compile.get().toInt()
  defaultConfig {
    minSdk = libs.versions.sdk.min.get().toInt()
    targetSdk = libs.versions.sdk.target.get().toInt()
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.jvm.bytecode.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.jvm.bytecode.get())
  }
  targetProjectPath = ":app"
}

kotlin {
  jvmToolchain(libs.versions.jvm.toolchain.get().toInt())
}

// generates and measures on the device that is connected (the agent's or your emulator, or a TV)
baselineProfile {
  useConnectedDevices = true
}

dependencies {
  implementation(libs.androidx.test.junit)
  implementation(libs.uiautomator)
  implementation(libs.benchmark.macro)
}

// the journeys read the id of the app under test, which differs per variant (nonMinifiedRelease, benchmarkRelease)
androidComponents {
  onVariants { variant ->
    val loader = variant.artifacts.getBuiltArtifactsLoader()
    variant.instrumentationRunnerArguments.put(
      "targetAppId",
      variant.testedApks.map { requireNotNull(loader.load(it)).applicationId },
    )
  }
}
