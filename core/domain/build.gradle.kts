plugins {
  id("convention.library")
  // for the tests' recorded fixtures
  alias(libs.plugins.kotlin.serialization)
}

dependencies {
  testImplementation(libs.serialization.json)
}
