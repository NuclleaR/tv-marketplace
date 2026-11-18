plugins {
  id("convention.library")
  alias(libs.plugins.metro)
  alias(libs.plugins.kotlin.serialization)
}

dependencies {
  api(libs.navigation3.runtime)
  api(platform(libs.compose.bom))
  api(libs.compose.runtime.saveable)
  implementation(libs.serialization.json)
}
