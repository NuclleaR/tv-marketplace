plugins {
  id("convention.library")
  id("convention.compose")
}

dependencies {
  api(libs.tv.material)
  api(projects.core.domain)
  implementation(projects.core.strings)
  implementation(libs.coil.compose)
  implementation(libs.coil.network.okhttp)
}
