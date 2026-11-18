plugins {
  id("convention.library")
  id("convention.compose")
}

dependencies {
  api(libs.tv.material)
  implementation(projects.core.strings)
}
