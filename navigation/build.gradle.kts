plugins {
  id("convention.library")
  alias(libs.plugins.metro)
  alias(libs.plugins.kotlin.serialization)
}

dependencies {
  api(libs.navigation3.runtime)
  implementation(libs.serialization.json)
}
