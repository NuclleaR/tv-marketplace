plugins {
  id("convention.library")
  alias(libs.plugins.metro)
}

// the FileProvider's paths
android {
  androidResources {
    enable = true
  }
}

dependencies {
  api(projects.core.installer.api)
  implementation(projects.core.common)
  implementation(projects.core.data.api)
  implementation(projects.core.strings)
  implementation(libs.androidx.core)
  implementation(libs.work.runtime)
  implementation(libs.lifecycle.process)
  implementation(libs.ktor.client.core)
  testImplementation(projects.core.testing)
  testImplementation(libs.ktor.client.mock)
  testImplementation(libs.work.testing)
}
