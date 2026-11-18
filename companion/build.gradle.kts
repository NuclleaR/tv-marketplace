plugins {
  id("convention.app")
  id("convention.compose")
}

android {
  defaultConfig {
    applicationId = "com.serhii.tvmarketplace.companion"
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
      signingConfig = signingConfigs.getByName("debug")
    }
  }
  dependenciesInfo {
    includeInApk = false
  }
}

dependencies {
  implementation(projects.core.domain)
  implementation(projects.core.protocol)
  implementation(libs.androidx.core)
  implementation(libs.datastore.preferences)
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.okhttp)
  testImplementation(libs.ktor.client.mock)
}
