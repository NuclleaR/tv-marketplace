plugins {
  id("convention.library")
  alias(libs.plugins.kotlin.serialization)
}

dependencies {
  api(libs.serialization.json)
}
