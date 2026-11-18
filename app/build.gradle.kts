plugins {
  id("convention.app")
  id("convention.compose")
  alias(libs.plugins.metro)
}

android {
  defaultConfig {
    applicationId = "com.serhii.tvmarketplace"
    versionCode = 1
    versionName = "1.0.0"
  }
  buildTypes {
    debug {
      applicationIdSuffix = ".debug"
    }
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
      // signs with the debug key so a local release build installs; replace with your own signing config
      signingConfig = signingConfigs.getByName("debug")
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
}
