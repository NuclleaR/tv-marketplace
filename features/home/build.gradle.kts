plugins {
  id("convention.library")
  id("convention.compose")
  alias(libs.plugins.metro)
}

dependencies {
  implementation(projects.core.common)
  implementation(projects.core.data.api)
  implementation(projects.core.installer.api)
  implementation(projects.core.linkserver)
  implementation(projects.core.strings)
  implementation(projects.core.ui)
  implementation(projects.navigation)
  testImplementation(projects.core.testing)
}
