plugins {
  id("convention.app")
  id("convention.compose")
  alias(libs.plugins.metro)
  alias(libs.plugins.baselineprofile)
}

android {
  defaultConfig {
    applicationId = "com.serhii.tvmarketplace"
  }
  buildTypes {
    debug {
      applicationIdSuffix = ".debug"
    }
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  dependenciesInfo {
    includeInApk = false
  }
}

dependencies {
  implementation(projects.core.common)
  implementation(projects.core.linkserver)
  implementation(projects.core.data.api)
  implementation(projects.core.data.impl)
  implementation(projects.core.strings)
  implementation(projects.core.ui)
  implementation(projects.navigation)
  implementation(projects.features.home)
  implementation(projects.features.addapp)
  implementation(projects.features.inbox)
  implementation(projects.features.settings)
  implementation(libs.navigation3.ui)
  implementation(libs.androidx.core)
  implementation(libs.lifecycle.process)
  implementation(libs.profileinstaller)
  baselineProfile(projects.baselineprofile)
}
