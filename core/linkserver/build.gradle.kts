plugins {
  id("convention.library")
  alias(libs.plugins.metro)
}

android {
  androidResources {
    enable = true
  }
}

dependencies {
  api(projects.core.domain)
  implementation(projects.core.common)
  implementation(projects.core.data.api)
  implementation(projects.core.protocol)
  implementation(libs.ktor.server.core)
  implementation(libs.ktor.server.cio)
  testImplementation(projects.core.testing)
  testImplementation(libs.ktor.server.testHost)
}
